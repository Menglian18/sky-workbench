#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
生成"先祖兑换树"数据（1.1 改进 · C2）。

为什么单独一个脚本：原来的 build_codex.py 只算了"每件物品的累计花费"，
把树的分叉结构压平了。现在要画兑换树，就必须把树形结构还原出来。

数据来源（本地那份 SkyGame-Data，MIT）：
  items.json         物品：guid / name / type（游戏内分类）/ icon（图标网址）
  nodes.json         兑换树节点：item + nw/n/ne 三个分支 + 花费(c 白蜡 / h 爱心 /
                     ac 升华蜡 / ec 活动货币 / sc 季节蜡烛 / sh 季节爱心)
  spirit-trees.json  每个"树"的根节点
  spirits.json       先祖：里面有一个字段指向它的树

输出：app/src/main/assets/codex/trees.json
  {"version":1, "trees":[{"spirit":"<先祖guid>", "root":0,
                          "nodes":[{"i":1,"zh":"斗篷","en":"Cape","type":"Outfit",
                                    "icon":"https://...","c":60,"h":0,"ac":0,"kids":[2,3]}]}]}

注意：**只导出图标网址，不下载图片**。App 打开时联网现取，安装包里不含任何游戏图片。
"""

import io
import json
import os
import sys

PROJECT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TC = os.environ.get('SKY_TOOLCHAIN') or os.path.abspath(os.path.join(PROJECT, '..', '..', '_toolchain'))
ASSETS = os.path.join(TC, 'skygame-data', 'package', 'assets')
OUT = os.path.join(PROJECT, 'app', 'src', 'main', 'assets', 'codex', 'trees.json')
CODEX_ITEMS = os.path.join(PROJECT, 'app', 'src', 'main', 'assets', 'codex', 'items.json')

COST_KEYS = ('c', 'h', 'ac', 'ec', 'sc', 'sh')


def load(name):
    path = os.path.join(ASSETS, name)
    with io.open(path, encoding='utf-8') as handle:
        return json.load(handle)['items']


def load_local_items():
    """本地已经生成好的图鉴（里面有中文部位名），按 guid 索引。
    兼容两种写法：裸数组，或包了一层 {"items": [...]}。"""
    if not os.path.exists(CODEX_ITEMS):
        return {}
    with io.open(CODEX_ITEMS, encoding='utf-8') as handle:
        data = json.load(handle)
    if isinstance(data, dict):
        data = data.get('items', [])
    result = {}
    for item in data:
        guid = item.get('guid')
        if guid:
            result[guid] = item
    return result


def build():
    items = {item['guid']: item for item in load('items.json') if item.get('guid')}
    nodes = {node['guid']: node for node in load('nodes.json') if node.get('guid')}
    trees = {tree['guid']: tree for tree in load('spirit-trees.json') if tree.get('guid')}
    spirits = load('spirits.json')
    local_items = load_local_items()

    tree_guids = set(trees.keys())
    output = []
    total_nodes = 0
    nodes_with_icon = 0
    spirits_without_tree = 0

    for spirit in spirits:
        spirit_guid = spirit.get('guid')
        if not spirit_guid:
            continue
        # 先祖身上哪个字段指向它的树：凡是值落在"树 guid 集合"里的就是
        root_guid = None
        for value in spirit.values():
            if isinstance(value, str) and value in tree_guids:
                root_guid = trees[value].get('node')
                break
        if not root_guid or root_guid not in nodes:
            spirits_without_tree += 1
            continue

        # 广度优先遍历这棵树，顺便去重（数据里偶有重复引用）
        order = []
        index_of = {}
        queue = [root_guid]
        seen = set()
        while queue:
            guid = queue.pop(0)
            if guid in seen or guid not in nodes:
                continue
            seen.add(guid)
            index_of[guid] = len(order)
            order.append(guid)
            node = nodes[guid]
            for child_key in ('nw', 'n', 'ne'):
                child = node.get(child_key)
                if isinstance(child, str):
                    queue.append(child)

        emitted = []
        for guid in order:
            node = nodes[guid]
            item_guid = node.get('item')
            source = items.get(item_guid, {})
            local = local_items.get(item_guid, {})
            icon = source.get('icon') or ''
            entry = {
                'i': index_of[guid],
                'item': item_guid or '',
                'zh': local.get('partZh') or '',
                'en': source.get('name') or '',
                'type': source.get('type') or '',
                'icon': icon,
                'kids': [
                    index_of[child]
                    for child in (node.get('nw'), node.get('n'), node.get('ne'))
                    if isinstance(child, str) and child in index_of
                ],
            }
            for key in COST_KEYS:
                value = node.get(key)
                if isinstance(value, int) and value:
                    entry[key] = value
            if icon:
                nodes_with_icon += 1
            emitted.append(entry)

        total_nodes += len(emitted)
        output.append({'spirit': spirit_guid, 'root': 0, 'nodes': emitted})

    result = {'version': 1, 'trees': output}
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with io.open(OUT, 'w', encoding='utf-8') as handle:
        handle.write(json.dumps(result, ensure_ascii=False, separators=(',', ':')))

    size_kb = os.path.getsize(OUT) / 1024.0
    print('先祖总数        : %d' % len(spirits))
    print('有兑换树的先祖  : %d' % len(output))
    print('没找到树的先祖  : %d' % spirits_without_tree)
    print('节点总数        : %d' % total_nodes)
    print('其中有图标的    : %d (%.0f%%)' % (nodes_with_icon, 100.0 * nodes_with_icon / max(1, total_nodes)))
    print('输出            : %s (%.1f KB)' % (OUT, size_kb))
    return 0


if __name__ == '__main__':
    sys.exit(build())
