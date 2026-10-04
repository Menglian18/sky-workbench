#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
构建期数据管线：生成"离线图鉴"数据。

做的事：
  1. 从 npm 取 SkyGame-Data（MIT 授权，纯英文，无图片），拿到物品 / 先祖 / 兑换树 / 节点 / 季节。
  2. 调 B站光遇WIKI 的接口，只取**中文名**：
     - 先祖中文名 ↔ 先祖英文名（两边对接的桥梁）
     - 季节中文名（用开启时间与 SkyGame-Data 的季节对齐）
  3. 沿兑换树累加成本，算出"每件物品要多少白蜡/爱心/升华蜡烛"和"这个先祖全收要多少"。
  4. 裁剪字段、压缩，写成 App 打包用的 assets/codex/*.json。

为什么价格必须来自 SkyGame-Data：WIKI 的蜡烛价格写在图片里（兑换图 PNG），文本里没有，抓不到。
许可证：SkyGame-Data 是 MIT；WIKI 内容是 CC BY-NC-SA 4.0（可免费用、须署名、禁止商用），
所以中文名单独输出到 data/zh-names/，并附来源说明。

用法：python tools/build_codex.py
"""

import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request

PROJECT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# SkyGame-Data 解包后的位置。默认取项目旁边的 _toolchain 目录，也可以用环境变量指定：
#   set SKY_TOOLCHAIN=D:\somewhere\_toolchain
TC = os.environ.get('SKY_TOOLCHAIN') or os.path.abspath(os.path.join(PROJECT, '..', '..', '_toolchain'))
SKYGAME_ASSETS = os.path.join(TC, 'skygame-data', 'package', 'assets')
OUT_ASSETS = os.path.join(PROJECT, 'app', 'src', 'main', 'assets', 'codex')
OUT_ZH = os.path.join(PROJECT, 'data', 'zh-names')

BWIKI_API = 'https://wiki.biligame.com/sky/api.php'

# 物品"部位"的中文说法（与 WIKI 上用的词保持一致）
TYPE_ZH = {
    'Hair': '发型',
    'Mask': '面具',
    'FaceAccessory': '面饰',
    'HairAccessory': '发饰',
    'Necklace': '项链',
    'Cape': '斗篷',
    'Outfit': '裤子',
    'Shoes': '鞋子',
    'Held': '手持',
    'Prop': '家具',
    'Emote': '表情',
    'Music': '乐谱',
    'Special': '特殊',
    'WingBuff': '光翼',
}


def log(msg):
    print(msg, flush=True)


def load_asset(name):
    path = os.path.join(SKYGAME_ASSETS, name)
    with open(path, encoding='utf-8') as handle:
        data = json.load(handle)
    # 这些文件的形状是 {"items": [...]}
    while isinstance(data, dict) and len(data) == 1:
        data = list(data.values())[0]
    return data


def bwiki(params, attempts=4):
    """调 WIKI 接口，带重试（对方会限流，报 567 之类）。"""
    url = BWIKI_API + '?' + urllib.parse.urlencode(params)
    last_error = None
    for attempt in range(1, attempts + 1):
        try:
            request = urllib.request.Request(
                url, headers={'User-Agent': 'Mozilla/5.0 (sky-workbench build script)'})
            with urllib.request.urlopen(request, timeout=60) as response:
                return json.loads(response.read().decode('utf-8'))
        except Exception as error:  # noqa: BLE001 - 构建脚本，失败就重试
            last_error = error
            wait = 3 * attempt
            log('    接口第 %d 次失败（%s），%d 秒后重试' % (attempt, error, wait))
            time.sleep(wait)
    raise last_error


def fetch_pages_content(titles):
    """一次取多个页面的 wikitext（接口支持一次 20 个），避免被限流。"""
    result = {}
    titles = [t for t in titles if t]
    for index in range(0, len(titles), 20):
        chunk = titles[index:index + 20]
        data = bwiki({
            'action': 'query',
            'prop': 'revisions',
            'rvslots': 'main',
            'rvprop': 'content',
            'format': 'json',
            'titles': '|'.join(chunk),
        })
        for page in (data.get('query', {}).get('pages') or {}).values():
            title = page.get('title')
            revisions = page.get('revisions')
            if title and revisions:
                try:
                    result[title] = revisions[0]['slots']['main']['*']
                except (KeyError, IndexError, TypeError):
                    pass
        log('      已批量取回 %d/%d 个页面' % (min(index + 20, len(titles)), len(titles)))
        time.sleep(0.6)
    return result


def wikitext(page):
    """取某个页面的 wikitext（取不到就返回空串）。"""
    data = bwiki({'action': 'parse', 'page': page, 'prop': 'wikitext', 'format': 'json'})
    if 'parse' not in data:
        return ''
    return data['parse']['wikitext']['*']


def parse_traveling_spirit_rows(text):
    """解析复刻页里的 `{{复刻项|...}}`，取出中文名 / 季节 / 物品部位。"""
    cleaned = re.sub(r'<!--.*?-->', '', text, flags=re.S)
    rows = []
    for block in cleaned.split('{{复刻项'):
        if '先祖中文名' not in block:
            continue
        name = re.search(r'先祖中文名\s*=\s*([^|}\n]+)', block)
        season = re.search(r'所属季节\s*=\s*([^|}\n]+)', block)
        parts = re.search(r'装扮\s*=\s*([^|}\n]+)', block)
        if not name:
            continue
        rows.append({
            'nameZh': name.group(1).strip(),
            'seasonZh': season.group(1).strip() if season else '',
            'parts': [p.strip() for p in (parts.group(1).split('、') if parts else []) if p.strip()],
        })
    return rows


def normalize_part(part):
    """把 wiki 上的部位说法归一化，便于和 SkyGame-Data 的物品类型对齐。"""
    if part in ('小型家具', '大型家具'):
        return '家具'
    return part


def build_zh_by_matching(rows, spirits, items, season_of_spirit, zh_season):
    """用"季节 + 物品部位组合"把中文名对上英文先祖名。

    只在**唯一候选**时才采纳，宁可少对几个，也不要张冠李戴。
    """
    # 季节中文 -> 季节英文短名
    zh_to_season_en = {}
    for season in season_of_spirit.values():
        zh = zh_season.get(season.get('date', ''))
        if zh:
            zh_to_season_en[zh] = season.get('shortName', '')

    # 先祖 -> 部位组合
    spirit_parts = {}
    for item in items:
        spirit_parts.setdefault(item['spiritId'], set()).add(normalize_part(item.get('partZh', '')))

    mapping = {}
    for row in rows:
        season_en = zh_to_season_en.get(row['seasonZh'])
        if not season_en:
            continue
        wanted = {normalize_part(p) for p in row['parts']}
        if not wanted:
            continue
        candidates = []
        for spirit in spirits:
            season = season_of_spirit.get(spirit['guid'])
            if not season or season.get('shortName') != season_en:
                continue
            if spirit_parts.get(spirit['guid'], set()) == wanted:
                candidates.append(spirit.get('name', ''))
        if len(candidates) == 1:
            mapping[candidates[0]] = row['nameZh']
    return mapping


def extract_name_pairs(text):
    """从一段 wikitext 里成对取出 `先祖中文名=` 与 `先祖英文名=`。

    注意：这两个字段在同一个 `{{...}}` 模板块里，但**分两行**写，
    所以要先按模板块切开再取值，不能按行判断。
    """
    pairs = {}
    cleaned = re.sub(r'<!--.*?-->', '', text, flags=re.S)
    for block in cleaned.split('{{'):
        if '先祖中文名' in block and '先祖英文名' in block:
            cn = re.search(r'先祖中文名\s*=\s*([^|}\n]+)', block)
            en = re.search(r'先祖英文名\s*=\s*([^|}\n]+)', block)
            if cn and en:
                pairs[en.group(1).strip()] = cn.group(1).strip()
    return pairs


def fetch_zh_spirit_names(season_titles):
    """先祖中文名 ↔ 英文名。

    WIKI 的语义标注覆盖率很低，但**每个季节页里都成对写着中英文名**，
    所以主要来源是逐个季节页提取；再补上"分类:先祖"里的常驻先祖页。
    """
    mapping = {}

    # 1) 语义查询里已经标注好的（可能被限流，失败不致命）
    try:
        data = bwiki({
            'action': 'ask',
            'format': 'json',
            'query': '[[分类:先祖]]|?先祖中文名|?先祖英文名|limit=500',
        })
        for title, item in (data.get('query', {}).get('results') or {}).items():
            printouts = item.get('printouts', {})
            cn = (printouts.get('先祖中文名') or [None])[0]
            en = (printouts.get('先祖英文名') or [None])[0]
            if cn and en:
                mapping[en.strip()] = cn.strip()
        log('    语义查询拿到 %d 对' % len(mapping))
    except Exception as error:  # noqa: BLE001
        log('    语义查询失败（%s），跳过' % error)

    # 2) 季节页里成对写着中英文名（用批量接口，少发请求）
    try:
        for title, content in fetch_pages_content(season_titles).items():
            mapping.update(extract_name_pairs(content))
    except Exception as error:  # noqa: BLE001
        log('    季节页抓取失败（%s），跳过' % error)
    log('    季节页读完，累计 %d 对' % len(mapping))

    return mapping


def fetch_zh_seasons():
    """季节中文名（用开启时间对齐），并返回季节页标题列表（中文名映射要从这些页面里取）。"""
    data = bwiki({
        'action': 'ask',
        'format': 'json',
        'query': '[[分类:奇妙之旅]]|?开启时间|?结束时间|limit=200',
    })
    mapping = {}
    titles = []
    for title, item in (data.get('query', {}).get('results') or {}).items():
        printouts = item.get('printouts', {})
        start = (printouts.get('开启时间') or [None])[0]
        titles.append(title)
        if start:
            mapping[start[:10]] = title
    return mapping, titles


def trace_costs(root_guid, node_by_guid):
    """沿兑换树累加成本：返回 {节点guid: (白蜡, 爱心, 升华)}。"""
    result = {}

    def walk(guid, candles, hearts, ascended):
        node = node_by_guid.get(guid)
        if node is None or guid in result:
            return
        candles += node.get('c', 0)
        hearts += node.get('h', 0)
        ascended += node.get('ac', 0)
        result[guid] = (candles, hearts, ascended)
        for child_key in ('nw', 'n', 'ne'):
            child = node.get(child_key)
            if child:
                walk(child, candles, hearts, ascended)

    walk(root_guid, 0, 0, 0)
    return result


def main():
    if not os.path.isdir(SKYGAME_ASSETS):
        log('找不到 SkyGame-Data：' + SKYGAME_ASSETS)
        return 1

    log('1/5 读取 SkyGame-Data …')
    spirits = load_asset('spirits.json')
    items = load_asset('items.json')
    nodes = load_asset('nodes.json')
    trees = load_asset('spirit-trees.json')
    seasons = load_asset('seasons.json')
    log('    先祖 %d / 物品 %d / 节点 %d / 兑换树 %d / 季节 %d'
        % (len(spirits), len(items), len(nodes), len(trees), len(seasons)))

    log('2/5 取 WIKI 中文名 …')
    try:
        zh_season, season_titles = fetch_zh_seasons()
    except Exception as error:  # noqa: BLE001
        log('    季节查询失败（%s），季节中文名先留空' % error)
        zh_season, season_titles = {}, []
    zh_spirit = fetch_zh_spirit_names(season_titles)
    log('    先祖中英文对照 %d 条；季节中文名 %d 条' % (len(zh_spirit), len(zh_season)))

    node_by_guid = {n['guid']: n for n in nodes}
    item_by_guid = {i['guid']: i for i in items}
    tree_by_guid = {t['guid']: t for t in trees}

    season_of_spirit = {}
    for season in seasons:
        for spirit_guid in season.get('spirits', []):
            season_of_spirit[spirit_guid] = season

    log('3/5 沿兑换树算成本 …')
    out_spirits = []
    out_items = []
    for spirit in spirits:
        tree_guid = spirit.get('tree')
        tree = tree_by_guid.get(tree_guid) if tree_guid else None
        # 有些"树"记录没有根节点（例如活动用的架子），跳过
        root_guid = tree.get('node') if tree else None
        if not root_guid:
            continue
        costs = trace_costs(root_guid, node_by_guid)
        if not costs:
            continue

        season = season_of_spirit.get(spirit['guid'])
        spirit_name_en = spirit.get('name', '')
        spirit_name_zh = zh_spirit.get(spirit_name_en, '')
        season_en = season.get('shortName', '') if season else ''
        season_zh = zh_season.get(season.get('date', '') if season else '', '')

        total_candles = sum(node_by_guid[g].get('c', 0) for g in costs)
        total_hearts = sum(node_by_guid[g].get('h', 0) for g in costs)
        total_ascended = sum(node_by_guid[g].get('ac', 0) for g in costs)

        count = 0
        for node_guid, (candles, hearts, ascended) in costs.items():
            item = item_by_guid.get(node_by_guid[node_guid].get('item'))
            if item is None or not item.get('name'):
                continue
            item_type = item.get('type', '')
            part_zh = TYPE_ZH.get(item_type, item_type)
            display_zh = '%s·%s' % (spirit_name_zh or spirit_name_en, part_zh)
            out_items.append({
                'guid': item['guid'],
                'nameEn': item['name'],
                'nameZh': display_zh,
                'type': item_type,
                'partZh': part_zh,
                'spiritId': spirit['guid'],
                'spiritNameEn': spirit_name_en,
                'spiritNameZh': spirit_name_zh,
                'seasonEn': season_en,
                'seasonZh': season_zh,
                'candles': candles,
                'hearts': hearts,
                'ascended': ascended,
            })
            count += 1

        out_spirits.append({
            'id': spirit['guid'],
            'nameEn': spirit_name_en,
            'nameZh': spirit_name_zh,
            'type': spirit.get('type', ''),
            'seasonEn': season_en,
            'seasonZh': season_zh,
            'totalCandles': total_candles,
            'totalHearts': total_hearts,
            'totalAscended': total_ascended,
            'itemCount': count,
        })

    log('    用复刻页补齐中文名 …')
    try:
        traveling_text = wikitext('旅行先祖回归记录')
    except Exception as error:  # noqa: BLE001
        log('      复刻页抓取失败（%s），改用本地缓存' % error)
        cache_path = os.path.join(TC, 'dl', 'bwiki_ts.wiki')
        traveling_text = open(cache_path, encoding='utf-8').read() if os.path.exists(cache_path) else ''

    if traveling_text:
        rows = parse_traveling_spirit_rows(traveling_text)
        matched = build_zh_by_matching(rows, spirits, out_items, season_of_spirit, zh_season)
        added = 0
        for name_en, name_zh in matched.items():
            if name_en and name_en not in zh_spirit:
                zh_spirit[name_en] = name_zh
                added += 1
        log('      复刻页 %d 行，唯一匹配新增 %d 条（现共 %d 条）'
            % (len(rows), added, len(zh_spirit)))

    # 把中文名回填到最终输出
    for spirit in out_spirits:
        if not spirit['nameZh']:
            spirit['nameZh'] = zh_spirit.get(spirit['nameEn'], '')
    for item in out_items:
        name_zh = zh_spirit.get(item['spiritNameEn'], '')
        if name_zh:
            item['spiritNameZh'] = name_zh
            item['nameZh'] = '%s·%s' % (name_zh, item['partZh'])

    out_seasons = [{
        'nameEn': s.get('name', ''),
        'nameZh': zh_season.get(s.get('date', ''), ''),
        'startDate': s.get('date', ''),
        'endDate': s.get('endDate', ''),
    } for s in seasons]

    log('    生成先祖 %d 个 / 物品 %d 件' % (len(out_spirits), len(out_items)))

    log('4/5 写出打包数据 …')
    os.makedirs(OUT_ASSETS, exist_ok=True)
    os.makedirs(OUT_ZH, exist_ok=True)

    def write_json(path, payload, compact=True):
        text = json.dumps(payload, ensure_ascii=False, separators=(',', ':') if compact else None)
        with open(path, 'w', encoding='utf-8', newline='\n') as handle:
            handle.write(text)
        return len(text.encode('utf-8'))

    size_spirits = write_json(os.path.join(OUT_ASSETS, 'spirits.json'), out_spirits)
    size_items = write_json(os.path.join(OUT_ASSETS, 'items.json'), out_items)
    size_seasons = write_json(os.path.join(OUT_ASSETS, 'seasons.json'), out_seasons)
    # 中文名单独放，附来源说明（WIKI 是 CC BY-NC-SA，不按代码授权发布）
    size_zh = write_json(os.path.join(OUT_ZH, 'zh-names.json'), {
        'spirits': zh_spirit,
        'seasons': zh_season,
    })

    log('    assets/codex/spirits.json  %6.1f KB' % (size_spirits / 1024))
    log('    assets/codex/items.json    %6.1f KB' % (size_items / 1024))
    log('    assets/codex/seasons.json  %6.1f KB' % (size_seasons / 1024))
    log('    data/zh-names/zh-names.json %6.1f KB' % (size_zh / 1024))

    log('5/5 抽查 …')
    with_zh = [s for s in out_spirits if s['nameZh']]
    log('    有中文名的先祖: %d / %d' % (len(with_zh), len(out_spirits)))
    for s in sorted(out_spirits, key=lambda x: -x['totalCandles'])[:3]:
        log('    全收最贵: %s(%s) 白蜡 %d / 爱心 %d / 升华 %d，共 %d 件'
            % (s['nameZh'] or s['nameEn'], s['nameEn'], s['totalCandles'], s['totalHearts'],
               s['totalAscended'], s['itemCount']))
    return 0


if __name__ == '__main__':
    sys.exit(main())
