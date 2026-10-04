#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
用"相似度配对"把 wiki 的官方物品名贴到图鉴上（1.2.5 第二轮思路）。

背景（上一轮探明的）：
  · wiki 的分类图鉴里有**官方中文名**（黄斗篷、白底TGC斗篷、蓝鸟斗篷…），
    但**大部分没写属于哪位先祖**，靠"先祖+位置"硬套只有 4% 可靠。
  · 图鉴里现在的名字是我**按术语表翻译**出来的（Blue Bird Ultimate Cape → 蓝鸟终极斗篷）。

新思路：两边**都是中文名**，所以可以按**字符重合度**配对：
  「蓝鸟终极斗篷」 ↔ wiki「蓝鸟斗篷」 重合 {蓝,鸟,斗,篷} → 相似度 0.8 ✓ 认
  相似度低于阈值的一律**不认**（宁可保留翻译名，也不要张冠李戴）。

只改数据（items.json 的 nameZh），不动代码。脚本会报告改了多少件、举几个例子。
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
ITEMS = os.path.join(CODEX, "items.json")
REPORT = os.path.join(PROJECT, "data", "zh-names", "item-names-official.json")

CATEGORIES = [
    ("斗篷", "Cape"), ("发型", "Hair"), ("面具", "Mask"), ("裤子", "Outfit"),
    ("发饰", "HairAccessory"), ("面饰", "FaceAccessory"), ("项链", "Necklace"),
    ("手持", "Held"), ("背饰", "Prop"), ("道具", "Prop"), ("动作", "Emote"),
    ("乐谱", "Music"), ("鞋子", "Shoes"),
]
ENTRY = re.compile(r"\[\[(?:文件|File|Image):([^|\]\n]+)\|([^|\]\n]*)([^\]]*)\]\]")
THRESHOLD = 0.55


def norm(text):
    text = (text or "").strip()
    text = re.sub(r"[\s　]+", "", text)
    text = re.sub(r"[:：·、,，.。!！?？\"'“”‘’\-—_/（）()\[\]【】]+", "", text)
    return text


def similarity(a, b):
    a, b = norm(a), norm(b)
    if len(a) < 2 or len(b) < 2:
        return 0.0
    common = sum((a.count(ch) if a.count(ch) < b.count(ch) else b.count(ch))
                 for ch in set(a) & set(b))
    return 2.0 * common / (len(a) + len(b))


def looks_like_name(text):
    if not text or len(text) > 14:
        return False
    if "=" in text or text.endswith(".png") or text.startswith("UI-"):
        return False
    if not re.search(r"[\u4e00-\u9fa5]", text):
        return False
    return True


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
            time.sleep(5 * (attempt + 1))
    return {}


def fetch_template(name):
    for candidate in (name, name + "图鉴"):
        res = get({"action": "query", "prop": "revisions", "rvprop": "content",
                   "rvslots": "main", "titles": "Template:" + candidate, "format": "json"})
        for _, page in (res.get("query", {}).get("pages", {}) or {}).items():
            try:
                text = page["revisions"][0]["slots"]["main"]["*"]
            except Exception:
                continue
            if text.strip():
                return candidate, text
        time.sleep(2)
    return None, ""


def main():
    with io.open(ITEMS, encoding="utf-8") as handle:
        items = json.load(handle)

    # 收集 wiki 官方名（按类型）
    gallery = {}
    for name, item_type in CATEGORIES:
        template_name, text = fetch_template(name)
        if not text:
            print("   %-6s 模板不存在/抓不到，跳过" % name)
            continue
        found = []
        for match in ENTRY.finditer(text):
            display = match.group(2).strip()
            if looks_like_name(display) and display not in found:
                found.append(display)
        gallery.setdefault(item_type, []).extend(found)
        print("   %-6s 模板 %-8s 官方名 %d 个" % (name, template_name, len(found)))
        time.sleep(2.5)

    total_gallery = sum(len(v) for v in gallery.values())
    print("")
    print("wiki 官方名合计: %d 个" % total_gallery)

    # 相似度配对：一个官方名只配一件物品，一件物品只收一个官方名
    pairs = []
    for item in items:
        item_type = item.get("type") or ""
        current = norm(item.get("nameZh") or "")
        if len(current) < 2:
            continue
        for official in gallery.get(item_type, []):
            score = similarity(current, official)
            if score >= THRESHOLD:
                pairs.append((score, item.get("guid"), official, current))
    pairs.sort(reverse=True)

    used_items, used_names, applied = set(), set(), {}
    for score, guid, official, current in pairs:
        if guid in used_items or official in used_names:
            continue
        used_items.add(guid)
        used_names.add(official)
        applied[guid] = (official, current, round(score, 2))

    changed = 0
    for item in items:
        guid = item.get("guid")
        if guid in applied:
            official, _, _ = applied[guid]
            if item.get("nameZh") != official:
                item["nameZh"] = official
                changed += 1

    with io.open(ITEMS, "w", encoding="utf-8") as handle:
        handle.write(json.dumps(items, ensure_ascii=False, separators=(",", ":")))

    os.makedirs(os.path.dirname(REPORT), exist_ok=True)
    with io.open(REPORT, "w", encoding="utf-8") as handle:
        handle.write(json.dumps(
            {g: {"官方名": v[0], "原翻译": v[1], "相似度": v[2]} for g, v in applied.items()},
            ensure_ascii=False, indent=1, sort_keys=True))

    print("相似度配对成功  : %d 件（阈值 %.2f）" % (len(applied), THRESHOLD))
    print("其中名字有变化  : %d 件" % changed)
    print("占全部物品      : %.0f%%" % (100.0 * len(applied) / max(1, len(items))))
    print("")
    print("样例（原翻译 → 官方名，相似度）:")
    for score, guid, official, current in pairs[:15]:
        if guid in applied:
            print("   %-16s → %-16s %.2f" % (current, official, score))
    print("明细已写入:", REPORT)
    return 0


if __name__ == "__main__":
    sys.exit(main())
