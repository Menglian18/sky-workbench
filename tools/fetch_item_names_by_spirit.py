#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
从"每位先祖自己的页面"抓官方物品中文名（1.2.5 第二轮）。

为什么换这条路：分类模板（Template:斗篷）虽然列出了官方中文名，但大部分条目**没有"所属先祖"**，
对到我图鉴的 2026 件物品上只对上 4%。而**先祖页面**里有一个"物品展示"分区，
列的正是**这位先祖**能给的东西 —— 按"先祖 → 我的物品"对应，匹配率会高得多。

做法：
  1. 按先祖抓页面（旅行先祖：<中文名>，批量 10 个一请求 + 每次间隔 3 秒，避免被限流）；
  2. 解析页面里 [[文件:UI-<分类>-<标识>.png|<官方中文名>|...]] 形式的条目；
  3. 用文件名里的分类（斗篷/发型/面具…）判断物品类型，再在该先祖的同类物品上按顺序贴名字；
  4. 与上一轮的结果**合并**（已有的不覆盖）。

输出：app/src/main/assets/codex/item-name-official.json（合并后）
"""

import io
import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request

API = "https://wiki.biligame.com/sky/api.php"
UA = {"User-Agent": "sky-workbench-personal/1.0 (personal offline helper)"}
PROJECT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CODEX = os.path.join(PROJECT, "app", "src", "main", "assets", "codex")
OUT = os.path.join(CODEX, "item-name-official.json")
STATE = os.path.join(PROJECT, "data", "zh-names", "item-names-official.json")

ENTRY = re.compile(r"\[\[(?:文件|File|Image):([^|\]\n]+)\|([^|\]\n]*)([^\]]*)\]\]")

# 文件名里的分类 → 数据里的 type
FILE_TYPE = {
    "斗篷": "Cape", "发型": "Hair", "面具": "Mask", "裤子": "Outfit", "套装": "Outfit",
    "发饰": "HairAccessory", "面饰": "FaceAccessory", "项链": "Necklace",
    "手持": "Held", "背饰": "Prop", "道具": "Prop", "动作": "Emote", "表情": "Emote",
    "乐谱": "Music", "乐器": "Music", "鞋子": "Shoes",
}


def norm(text):
    text = re.sub(r"[（(\[【].*?[）)\]】]", "", text or "")
    text = re.sub(r"[\s　]+", "", text)
    text = re.sub(r"[:：·、,，.。!！?？\"'“”‘’\-—_/]+", "", text)
    return text.lower().strip()


def get(params, retries=4):
    url = API + "?" + urllib.parse.urlencode(params)
    request = urllib.request.Request(url, headers=UA)
    for attempt in range(retries):
        try:
            with urllib.request.urlopen(request, timeout=45) as response:
                return json.loads(response.read().decode("utf-8"))
        except Exception:
            if attempt == retries - 1:
                return {}
            time.sleep(4 * (attempt + 1))
    return {}


def parse_page(text, spirit_items):
    """spirit_items: {type: [itemGuid, ...]}（这位先祖的、按顺序）"""
    result = {}
    used = {}
    for match in ENTRY.finditer(text):
        filename = match.group(1).strip()
        display = match.group(2).strip()
        if (not display) or display.startswith("UI-") or "=" in display or display.endswith(".png"):
            continue
        parts = filename.replace(".png", "").split("-")
        file_type = FILE_TYPE.get(parts[1].strip()) if len(parts) >= 2 else None
        if not file_type:
            continue
        pool = spirit_items.get(file_type) or []
        start = used.get(file_type, 0)
        if start >= len(pool):
            continue
        result[pool[start]] = display
        used[file_type] = start + 1
    return result


def main():
    spirits = json.load(io.open(os.path.join(CODEX, "spirits.json"), encoding="utf-8"))
    items = json.load(io.open(os.path.join(CODEX, "items.json"), encoding="utf-8"))

    # 每位先祖的物品（按类型分组）
    by_spirit = {}
    for item in items:
        spirit_id = item.get("spiritId") or ""
        item_type = item.get("type") or ""
        by_spirit.setdefault(spirit_id, {}).setdefault(item_type, []).append(item.get("guid"))

    # 已有的官方名（上一轮的结果），不覆盖
    official = {}
    if os.path.exists(OUT):
        official = json.load(io.open(OUT, encoding="utf-8"))

    # 只抓"有中文名且这一位还有没拿到官方名的物品"的先祖
    targets = []
    for spirit in spirits:
        guid = spirit.get("guid") or ""
        name = (spirit.get("nameZh") or "").strip()
        if not guid or not name:
            continue
        pool = by_spirit.get(guid) or {}
        if any(item_guid not in official for guids in pool.values() for item_guid in guids):
            targets.append((guid, name))

    print("要抓的先祖页面: %d 个" % len(targets))
    got = 0
    for start in range(0, len(targets), 10):
        batch = targets[start:start + 10]
        titles = "|".join("旅行先祖：" + name for _, name in batch)
        res = get({"action": "query", "prop": "revisions", "rvprop": "content",
                   "rvslots": "main", "titles": titles, "format": "json"})
        pages = res.get("query", {}).get("pages", {}) or {}
        if not pages:
            print("   第 %d 批失败（可能被限流），稍后重试" % (start // 10 + 1))
            time.sleep(8)
            continue
        title_to_guid = {name: guid for guid, name in batch}
        for _, page in pages.items():
            title = page.get("title", "")
            spirit_name = title.replace("旅行先祖：", "").strip()
            guid = title_to_guid.get(spirit_name)
            if not guid:
                continue
            try:
                text = page["revisions"][0]["slots"]["main"]["*"]
            except Exception:
                continue
            found = parse_page(text, by_spirit.get(guid) or {})
            if found:
                official.update(found)
                got += len(found)
        print("   第 %d 批完成，累计新增 %d 条" % (start // 10 + 1, got))
        time.sleep(3)

    with io.open(OUT, "w", encoding="utf-8") as handle:
        handle.write(json.dumps(official, ensure_ascii=False, separators=(",", ":"), sort_keys=True))
    os.makedirs(os.path.dirname(STATE), exist_ok=True)
    with io.open(STATE, "w", encoding="utf-8") as handle:
        handle.write(json.dumps(official, ensure_ascii=False, indent=1, sort_keys=True))

    print("")
    print("官方物品名合计: %d / %d (%.0f%%)" % (
        len(official), len(items), 100.0 * len(official) / max(1, len(items))))
    print("输出:", OUT)
    return 0


if __name__ == "__main__":
    sys.exit(main())
