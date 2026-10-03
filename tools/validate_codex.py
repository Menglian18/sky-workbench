#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
校验构建期生成的图鉴数据是否合格。

为什么需要它：图鉴数据是"生成"出来的，生成脚本一改就可能悄悄产出坏数据
（价格算成负数、物品挂在不存在的前先祖下面、日期格式乱掉……）。
这个脚本对**将要打进 App 的文件本身**做检查，不过就返回非零退出码，可以拿来卡构建。

注意：物品的花费分好几种货币 —— 白蜡(c) / 爱心(h) / 升华蜡(ac) / 季节蜡烛(sc) /
季节爱心(sh) / 活动货币(ec)。本项目的"心愿单算账"只关心前三种（能攒、能规划的那三种），
所以**有大量物品的白蜡花费是 0 是正常的**（它们花的是季节蜡烛或活动货币），
这里只报事实，不当成错误。

用法：python tools/validate_codex.py
"""

import json
import os
import re
import sys

PROJECT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CODEX_DIR = os.path.join(PROJECT, 'app', 'src', 'main', 'assets', 'codex')
ZH_FILE = os.path.join(PROJECT, 'data', 'zh-names', 'zh-names.json')

DATE_RE = re.compile(r'^\d{4}-\d{2}-\d{2}$')


def load(path):
    with open(path, encoding='utf-8') as handle:
        return json.load(handle)


def main():
    problems = []
    notes = []

    for name in ('spirits.json', 'items.json', 'seasons.json'):
        path = os.path.join(CODEX_DIR, name)
        if not os.path.exists(path):
            problems.append('缺少文件：%s' % path)
    if problems:
        for problem in problems:
            print('[x]', problem)
        return 1

    spirits = load(os.path.join(CODEX_DIR, 'spirits.json'))
    items = load(os.path.join(CODEX_DIR, 'items.json'))
    seasons = load(os.path.join(CODEX_DIR, 'seasons.json'))

    # ---- 先祖 ----
    spirit_ids = set()
    for spirit in spirits:
        sid = spirit.get('id') or ''
        if not sid:
            problems.append('有先祖缺 id')
            continue
        if sid in spirit_ids:
            problems.append('先祖 id 重复：%s' % sid)
        spirit_ids.add(sid)
        if not (spirit.get('nameEn') or '').strip():
            problems.append('先祖缺英文名：%s' % sid)
        for key in ('totalCandles', 'totalHearts', 'totalAscended'):
            value = spirit.get(key, 0)
            if not isinstance(value, int) or value < 0:
                problems.append('先祖 %s 的 %s 不合法：%r' % (sid, key, value))
        if spirit.get('itemCount', 0) < 0:
            problems.append('先祖 %s 的物品数为负' % sid)

    # ---- 物品 ----
    seen_pairs = set()
    costed = 0
    zero_cost = 0
    for item in items:
        guid = item.get('guid') or ''
        sid = item.get('spiritId') or ''
        if not guid:
            problems.append('有物品缺 guid')
            continue
        if sid not in spirit_ids:
            problems.append('物品 %s 挂在不存在的先祖 %s 下面' % (guid, sid))
        if not (item.get('nameEn') or '').strip():
            problems.append('物品缺英文名：%s' % guid)
        if not (item.get('partZh') or '').strip():
            problems.append('物品缺中文部位：%s' % guid)
        pair = (sid, guid)
        if pair in seen_pairs:
            problems.append('同一个先祖下物品重复：%s / %s' % (sid, guid))
        seen_pairs.add(pair)

        for key in ('candles', 'hearts', 'ascended'):
            value = item.get(key, 0)
            if not isinstance(value, int) or value < 0:
                problems.append('物品 %s 的 %s 不合法：%r' % (guid, key, value))
        total = item.get('candles', 0) + item.get('hearts', 0) + item.get('ascended', 0)
        if total > 0:
            costed += 1
        else:
            zero_cost += 1

    # 每件物品的"路径花费"不该超过它所属先祖的"全收花费"
    spirit_total = {s['id']: s.get('totalCandles', 0) for s in spirits}
    for item in items:
        total = spirit_total.get(item.get('spiritId'), 0)
        if item.get('candles', 0) > total:
            problems.append(
                '物品 %s 的路径花费(%d) 超过了先祖全收(%d)'
                % (item.get('guid'), item.get('candles', 0), total))

    # ---- 季节 ----
    for season in seasons:
        for key in ('startDate', 'endDate'):
            value = season.get(key, '')
            if not DATE_RE.match(value or ''):
                problems.append('季节 %s 的 %s 不是 yyyy-MM-dd：%r'
                                % (season.get('nameEn'), key, value))
        if (season.get('startDate') or '') > (season.get('endDate') or ''):
            problems.append('季节 %s 的开始日晚于结束日' % season.get('nameEn'))

    # ---- 中文名 ----
    zh = load(ZH_FILE) if os.path.exists(ZH_FILE) else {}
    zh_spirits = zh.get('spirits', {}) if isinstance(zh, dict) else {}
    with_zh = sum(1 for s in spirits if (s.get('nameZh') or '').strip())
    ratio = (with_zh / len(spirits)) if spirits else 0.0

    # ---- 事实报告 ----
    notes.append('先祖 %d 个，其中 %d 个有中文名（%.0f%%）' % (len(spirits), with_zh, ratio * 100))
    notes.append('物品 %d 件：其中 %d 件花白蜡/爱心/升华蜡，%d 件花季节蜡烛或活动货币'
                 % (len(items), costed, zero_cost))
    notes.append('季节 %d 个' % len(seasons))
    notes.append('中文名映射表 %d 条' % len(zh_spirits))

    for note in notes:
        print('-', note)

    if problems:
        print('')
        for problem in problems[:40]:
            print('[x]', problem)
        print('')
        print('校验不通过：%d 个问题' % len(problems))
        return 1

    print('')
    print('[OK] 图鉴数据校验通过')
    return 0


if __name__ == '__main__':
    sys.exit(main())
