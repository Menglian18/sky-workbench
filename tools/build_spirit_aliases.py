#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
生成"先祖别名表"（1.2.3 更新：兑换树要直接显示当前这位先祖的树）。

问题：复刻页上的先祖名是官方中文译名（蟹语者、火先知、彩纸表亲…），
和 skygame-data 里先祖的英文名对不上，靠名字直接匹配只能对上约 77%。

办法：用**季节**做一对一配对 ——
  · 复刻页每一行都写着"所属季节"（例：圣岛季）；
  · 我的先祖数据里也有中文季节名；
  · 于是可以按季节分组，把"没配上的先祖名"和"该季节里没被占用的先祖"配起来；
  · **只有在 1 对 1（不歧义）时才写进别名表**，宁可少配也不配错。

输出：app/src/main/assets/codex/spirit-aliases.json  {"先祖名": "guid", ...}
"""

import io
import json
import os
import re
import sys

PROJECT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TC = os.environ.get('NANA_TOOLCHAIN') or os.path.abspath(os.path.join(PROJECT, '..', '..', '_toolchain'))
CODEX = os.path.join(PROJECT, 'app', 'src', 'main', 'assets', 'codex')
OUT = os.path.join(CODEX, 'spirit-aliases.json')
WIKI = os.path.join(TC, 'dl', 'bwiki_ts.wiki')


def norm(text):
    text = re.sub(r"[（(\[【].*?[）)\]】]", "", text or "")
    text = re.sub(r"[\s　]+", "", text)
    text = re.sub(r"[:：·、,，.。!！?？\"'“”‘’\-—_/]+", "", text)
    return text.lower().strip()


def main():
    codex_spirits = json.load(io.open(os.path.join(CODEX, 'spirits.json'), encoding='utf-8'))
    trees = json.load(io.open(os.path.join(CODEX, 'trees.json'), encoding='utf-8'))
    tree_ids = set(t['spirit'] for t in trees['trees'])
    usable = [s for s in codex_spirits if s.get('guid') in tree_ids]

    # 名字索引（中文名 + 英文名）
    by_name = {}
    for spirit in usable:
        for key in (spirit.get('nameZh') or '', spirit.get('nameEn') or ''):
            k = norm(key)
            if k:
                by_name.setdefault(k, spirit)

    if not os.path.exists(WIKI):
        print('找不到复刻页数据:', WIKI)
        return 1
    wiki = io.open(WIKI, encoding='utf-8', errors='ignore').read()

    # 解析复刻页的每一行：先祖名 + 所属季节
    rows = []
    for block in re.findall(r"\{\{[^{}]*\}\}", wiki):
        fields = dict(re.findall(r"([^|={}]{2,10})=([^|}]*)", block))
        name = ''
        season = ''
        for key, value in fields.items():
            value = value.strip()
            if not value:
                continue
            if '先祖' in key and '名' in key and not name:
                name = value
            if value.endswith('季') and not season:
                season = value
        if name and season:
            rows.append((name, season))

    # 已经被名字直接对上的先祖，先占位
    taken = set()
    unmatched = []
    for name, season in rows:
        key = norm(name)
        hit = by_name.get(key)
        if hit is None:
            hit = next(
                (s for k, s in by_name.items() if len(k) >= 3 and (k in key or key in k)),
                None,
            )
        if hit is not None:
            taken.add(hit['guid'])
        else:
            unmatched.append((name, season))

    # 用"中英对照表"当桥：中文名 → 英文名 → 先祖
    # （zh-names.json 里的 140 条就是从 wiki 抓的官方中英名，比季节更可靠）
    zh_path = os.path.join(PROJECT, 'data', 'zh-names', 'zh-names.json')
    chinese_to_english = {}
    if os.path.exists(zh_path):
        zh = json.load(io.open(zh_path, encoding='utf-8'))
        for english, chinese in (zh.get('spirits', {}) if isinstance(zh, dict) else {}).items():
            k = norm(chinese)
            if k and english:
                chinese_to_english.setdefault(k, english)
    english_to_spirit = {}
    for spirit in usable:
        k = norm(spirit.get('nameEn') or '')
        if k:
            english_to_spirit.setdefault(k, spirit)

    aliases = {}
    unresolved = []
    for name, season in unmatched:
        english = chinese_to_english.get(norm(name))
        spirit = english_to_spirit.get(norm(english)) if english else None
        if spirit is not None and spirit['guid'] not in taken:
            aliases[name] = spirit['guid']
            taken.add(spirit['guid'])
        else:
            unresolved.append('%s（%s，%s）' % (name, season, ('英文名 ' + english) if english else '对照表里没有'))

    with io.open(OUT, 'w', encoding='utf-8') as handle:
        handle.write(json.dumps(aliases, ensure_ascii=False, separators=(',', ':'), sort_keys=True))

    print('复刻页条目        : %d' % len(rows))
    print('名字直接对上      : %d' % (len(rows) - len(unmatched)))
    print('按季节一对一配出  : %d' % len(aliases))
    print('仍无法确定        : %d' % len(unresolved))
    print('输出              : %s' % OUT)
    if aliases:
        print('')
        print('配出来的别名（前 15 条）:')
        for name, guid in list(aliases.items())[:15]:
            spirit = next((s for s in usable if s['guid'] == guid), {})
            print('   %-14s → %-16s (%s)' % (name, spirit.get('nameEn', '?')[:16], spirit.get('seasonZh', '')))
    if unresolved:
        print('')
        print('仍不确定的:')
        for item in unresolved[:15]:
            print('   ' + item)
    return 0


if __name__ == '__main__':
    sys.exit(main())
