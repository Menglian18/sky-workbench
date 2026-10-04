#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
给图鉴数据补上"游戏内分类 + 图标网址"（1.1 改进 · C3）。

原来的 items.json 只有物品名、部位中文名、花费和所属先祖，
没有"这是斗篷还是发型"（分类），也没有图标网址 —— 图鉴要做成"按游戏分类的图片网格"就缺这两样。

数据来自本地那份 SkyGame-Data 的 items.json（MIT）：
  type = 游戏内分类（Outfit / Mask / Hair / Cape ...）
  icon = 图标网址（只导出网址，不下载图片）

就地更新：app/src/main/assets/codex/items.json
"""

import io
import json
import os
import sys

PROJECT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TC = os.environ.get('SKY_TOOLCHAIN') or os.path.abspath(os.path.join(PROJECT, '..', '..', '_toolchain'))
SOURCE = os.path.join(TC, 'skygame-data', 'package', 'assets', 'items.json')
TARGET = os.path.join(PROJECT, 'app', 'src', 'main', 'assets', 'codex', 'items.json')


def main():
    if not os.path.exists(SOURCE):
        print('找不到来源文件:', SOURCE)
        return 1
    with io.open(SOURCE, encoding='utf-8') as handle:
        source = json.load(handle)['items']
    by_guid = {item['guid']: item for item in source if item.get('guid')}

    with io.open(TARGET, encoding='utf-8') as handle:
        data = json.load(handle)
    if isinstance(data, dict):
        data = data.get('items', [])

    got_type = 0
    got_icon = 0
    types = {}
    for entry in data:
        item = by_guid.get(entry.get('guid'), {})
        kind = item.get('type') or ''
        icon = item.get('icon') or ''
        entry['type'] = kind
        entry['icon'] = icon
        if kind:
            got_type += 1
            types[kind] = types.get(kind, 0) + 1
        if icon:
            got_icon += 1

    with io.open(TARGET, 'w', encoding='utf-8') as handle:
        handle.write(json.dumps(data, ensure_ascii=False, separators=(',', ':')))

    print('物品总数      : %d' % len(data))
    print('有分类的      : %d' % got_type)
    print('有图标网址的  : %d (%.0f%%)' % (got_icon, 100.0 * got_icon / max(1, len(data))))
    print('分类数        : %d' % len(types))
    top = sorted(types.items(), key=lambda kv: -kv[1])[:12]
    print('分类分布(前12): ' + ', '.join('%s %d' % (k, v) for k, v in top))
    print('输出          : %s (%.1f KB)' % (TARGET, os.path.getsize(TARGET) / 1024.0))
    return 0


if __name__ == '__main__':
    sys.exit(main())
