#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
给图鉴的先祖数据补上 guid 和中文名（1.2 改进 · 第 1 条：兑换树看不到）。

问题：`codex/spirits.json` 里只有 nameEn / nameZh / id 等，**没有 guid**，
而兑换树（trees.json）是按 guid 索引的 —— 于是 App 里"先祖名 → 兑换树"永远查不到，
用户点了"看兑换树"什么也看不到。

做法：拿本地那份 SkyGame-Data 的 spirits.json 按**英文名**对齐，把 guid 补进去；
中文名优先用已有的 nameZh，缺的从 data/zh-names/zh-names.json 里补。

就地更新：app/src/main/assets/codex/spirits.json
"""

import io
import json
import os
import sys

PROJECT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TC = os.environ.get('SKY_TOOLCHAIN') or os.path.abspath(os.path.join(PROJECT, '..', '..', '_toolchain'))
SOURCE = os.path.join(TC, 'skygame-data', 'package', 'assets', 'spirits.json')
CODEX = os.path.join(PROJECT, 'app', 'src', 'main', 'assets', 'codex', 'spirits.json')
ZH = os.path.join(PROJECT, 'data', 'zh-names', 'zh-names.json')


def norm(text):
    return (text or '').replace(' ', '').lower()


def main():
    with io.open(SOURCE, encoding='utf-8') as handle:
        source = json.load(handle)
        if isinstance(source, dict):
            source = source.get('items', [])
    by_name = {}
    for spirit in source:
        name = spirit.get('name') or ''
        if name:
            by_name[norm(name)] = spirit

    zh = {}
    if os.path.exists(ZH):
        with io.open(ZH, encoding='utf-8') as handle:
            data = json.load(handle)
        zh = data.get('spirits', {}) if isinstance(data, dict) else {}
    zh_by_norm = {norm(k): v for k, v in zh.items()}

    with io.open(CODEX, encoding='utf-8') as handle:
        codex = json.load(handle)
    if isinstance(codex, dict):
        codex = codex.get('items', [])

    matched = 0
    got_zh = 0
    for entry in codex:
        name = entry.get('nameEn') or ''
        spirit = by_name.get(norm(name))
        entry['guid'] = (spirit or {}).get('guid', '')
        if entry['guid']:
            matched += 1
        if not (entry.get('nameZh') or '').strip():
            guess = zh_by_norm.get(norm(name)) or zh.get(name)
            if guess:
                entry['nameZh'] = guess
        if (entry.get('nameZh') or '').strip():
            got_zh += 1

    with io.open(CODEX, 'w', encoding='utf-8') as handle:
        handle.write(json.dumps(codex, ensure_ascii=False, separators=(',', ':')))

    print('图鉴先祖总数    : %d' % len(codex))
    print('补上 guid 的     : %d' % matched)
    print('有中文名的       : %d (%.0f%%)' % (got_zh, 100.0 * got_zh / max(1, len(codex))))
    print('中文名映射表    : %d 条' % len(zh))
    print('输出            : %s' % CODEX)
    return 0


if __name__ == '__main__':
    sys.exit(main())
