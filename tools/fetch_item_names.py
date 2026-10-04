#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
从 BWIKI 抓"官方物品中文名"（1.2.5：把图鉴里逐词翻译的名字换成官方名）。

发现：wiki 的分类模板（如 Template:斗篷）里是完整图鉴，每一项形如
    [[文件:UI-斗篷-引蝶人.png|黄斗篷|class=img-kk|center|link=云野#引蝶人]]
                              ↑官方中文名                        ↑所属先祖（页面#锚点）
所以一次请求就能拿到「官方中文名 + 所属先祖 + 分类」三要素。

做法：
  1. 逐分类抓模板（斗篷/发型/面具/裤子/发饰/面饰/项链/手持/道具/动作…，名字不存在的自动跳过）；
  2. 解析出上面三要素；
  3. 用"分类 + 所属先祖"对应到图鉴里的物品，写出 guid → 官方中文名；
  4. 抓不到的保留原来的名字（脚本会报告覆盖率）。

输出：app/src/main/assets/codex/item-name-official.json
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
UA = {"User-Agent": "nana-workbench-personal/1.0 (personal offline helper)"}
PROJECT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CODEX = os.path.join(PROJECT, "app", "src", "main", "assets", "codex")
OUT = os.path.join(CODEX, "item-name-official.json")

# 分类名 → 数据里的 type
CATEGORIES = [
    ("斗篷", "Cape"), ("发型", "Hair"), ("面具", "Mask"), ("裤子", "Outfit"),
    ("发饰", "HairAccessory"), ("面饰", "FaceAccessory"), ("项链", "Necklace"),
    ("手持", "Held"), ("背饰", "Prop"), ("道具", "Prop"), ("动作", "Emote"),
    ("乐谱", "Music"), ("鞋子", "Shoes"),
]

ENTRY = re.compile(r"\[\[(?:文件|File|Image):([^|\]\n]+)\|([^|\]\n]*)([^\]]*)\]\]")
LINK = re.compile(r"link=([^|\]\n]+)")


def norm(text):
    text = re.sub(r"[（(\[【].*?[）)\]】]", "", text or "")
    text = re.sub(r"[\s　]+", "", text)
    text = re.sub(r"[:：·、,，.。!！?？\"'“”‘’\-—_/]+", "", text)
    return text.lower().strip()


def get(params, retries=3):
    url = API + "?" + urllib.parse.urlencode(params)
    request = urllib.request.Request(url, headers=UA)
    for attempt in range(retries):
        try:
            with urllib.request.urlopen(request, timeout=40) as response:
                return json.loads(response.read().decode("utf-8"))
        except Exception:
            if attempt == retries - 1:
                return {}
            time.sleep(2)
    return {}


def fetch_template(name):
    for candidate in (name, name + "图鉴", name + "装扮", name + "列表"):
        res = get({"action": "query", "prop": "revisions", "rvprop": "content",
                   "rvslots": "main", "titles": "Template:" + candidate, "format": "json"})
        for _, page in (res.get("query", {}).get("pages", {}) or {}).items():
            try:
                text = page["revisions"][0]["slots"]["main"]["*"]
            except Exception:
                continue
            if text.strip():
                return candidate, text
        time.sleep(0.8)
    return None, ""


def main():
    spirits = json.load(io.open(os.path.join(CODEX, "spirits.json"), encoding="utf-8"))
    items = json.load(io.open(os.path.join(CODEX, "items.json"), encoding="utf-8"))
    trees = json.load(io.open(os.path.join(CODEX, "trees.json"), encoding="utf-8"))
    tree_ids = set(t["spirit"] for t in trees["trees"])

    # 先祖名 → guid（中文名 + 英文名 + wiki 别名）
    name_to_guid = {}
    for spirit in spirits:
        if spirit.get("guid") not in tree_ids:
            continue
        for key in (spirit.get("nameZh") or "", spirit.get("nameEn") or ""):
            k = norm(key)
            if k:
                name_to_guid.setdefault(k, spirit["guid"])
    pairs_path = os.path.join(CODEX, "spirit-name-pairs.json")
    if os.path.exists(pairs_path):
        pairs = json.load(io.open(pairs_path, encoding="utf-8"))
        en_to_guid = {norm(s.get("nameEn"), ) if False else norm(s.get("nameEn") or ""): s["guid"]
                      for s in spirits if s.get("guid") in tree_ids}
        for english, chinese in pairs.items():
            guid = en_to_guid.get(norm(english))
            if guid:
                name_to_guid.setdefault(norm(chinese), guid)

    # 每件物品：guid → (spiritId, type)
    item_index = {}
    for item in items:
        item_index[item.get("guid")] = (item.get("spiritId") or "", item.get("type") or "")

    official = {}
    report = []
    for name, item_type in CATEGORIES:
        template_name, text = fetch_template(name)
        if not text:
            report.append("   %-8s 模板不存在，跳过" % name)
            continue
        entries = []
        for match in ENTRY.finditer(text):
            filename, display, rest = match.group(1).strip(), match.group(2).strip(), match.group(3)
            # "class=img-kk" 这种是样式参数，不是名字，丢掉
            if (not display) or display.startswith("UI-") or "=" in display:
                continue
            link = LINK.search(rest)
            anchor = link.group(1).split("#")[-1].strip() if link else ""
            # 兜底：图片名形如 UI-斗篷-引蝶人.png，中间那段常常就是先祖名
            parts = filename.replace(".png", "").split("-")
            file_spirit = parts[2].strip() if len(parts) >= 3 else ""
            entries.append((display, anchor or file_spirit))
        # 按"所属先祖 + 分类"把官方名对到物品上
        by_spirit = {}
        for display, anchor in entries:
            key = norm(anchor)
            guid = name_to_guid.get(key) if key else None
            if guid is None and len(key) >= 3:
                guid = next((g for n, g in name_to_guid.items()
                             if len(n) >= 3 and (n in key or key in n)), None)
            if guid:
                by_spirit.setdefault(guid, []).append(display)
        matched = 0
        for spirit_id, names in by_spirit.items():
            candidates = [g for g, (sid, t) in item_index.items()
                          if sid == spirit_id and t == item_type and g not in official]
            for guid, display in zip(candidates, names):
                # 官方名如果太短（如"初始"）也照收，它是 wiki 上的显示名
                official[guid] = display
                matched += 1
        report.append("   %-8s 模板 %-10s 条目 %3d，对上物品 %3d" % (name, template_name, len(entries), matched))
        time.sleep(1.2)

    with io.open(OUT, "w", encoding="utf-8") as handle:
        handle.write(json.dumps(official, ensure_ascii=False, separators=(",", ":"), sort_keys=True))

    print("分类模板抓取情况:")
    print("\n".join(report))
    print("")
    print("拿到官方中文名的物品: %d / %d (%.0f%%)" % (
        len(official), len(items), 100.0 * len(official) / max(1, len(items))))
    print("输出:", OUT)
    if official:
        print("样例:")
        for guid, name in list(official.items())[:12]:
            print("   %-24s  (guid %s)" % (name, guid))
    return 0


if __name__ == "__main__":
    sys.exit(main())
