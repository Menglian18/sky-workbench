#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
给图鉴的先祖数据补上"先祖照片地址"（1.2.2 改进 · 第 3 条）。

数据来源：本地那份 SkyGame-Data 的 spirits.json 里有 imageUrl 字段（256 位先祖都有值），
指向 wiki 上的先祖照片。

注意：**只导出网址，不下载图片**。App 打开时联网现取（和物品图标一样），
所以安装包里仍然不含任何游戏图片。

就地更新：app/src/main/assets/codex/spirits.json（加一个 imageUrl 字段）
"""

import io
import json
import os
import sys

PROJECT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TC = os.environ.get('NANA_TOOLCHAIN') or os.path.abspath(os.path.join(PROJECT, '..', '..', '_toolchain'))
SOURCE = os.path.join(TC, 'skygame-data', 'package', 'assets', 'spirits.json')
CODEX = os.path.join(PROJECT, 'app', 'src', 'main', 'assets', 'codex', 'spirits.json')


def main():
    with io.open(SOURCE, encoding='utf-8') as handle:
        source = json.load(handle)['items']
    by_guid = {s['guid']: s for s in source if s.get('guid')}

    with io.open(CODEX, encoding='utf-8') as handle:
        codex = json.load(handle)
    if isinstance(codex, dict):
        codex = codex.get('items', [])

    got = 0
    for entry in codex:
        guid = entry.get('guid') or ''
        entry['imageUrl'] = (by_guid.get(guid) or {}).get('imageUrl') or ''
        if entry['imageUrl']:
            got += 1

    with io.open(CODEX, 'w', encoding='utf-8') as handle:
        handle.write(json.dumps(codex, ensure_ascii=False, separators=(',', ':')))

    print('先祖总数        : %d' % len(codex))
    print('有照片地址的    : %d (%.0f%%)' % (got, 100.0 * got / max(1, len(codex))))
    print('来源里有照片的  : %d / %d' % (sum(1 for s in source if s.get('imageUrl')), len(source)))
    print('输出            : %s' % CODEX)
    return 0


if __name__ == '__main__':
    sys.exit(main())
