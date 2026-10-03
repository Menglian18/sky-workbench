#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
把物品的英文名翻成中文（1.2.2 改进 · 第 2 条）。

做法（两层）：
  1. **先替换先祖名**：物品名里常含先祖的英文名（如 Indifferent Alchemist Cape），
     而 data/zh-names/zh-names.json 里有 140 位先祖的**官方中英对照**，
     直接换成中文，比逐词翻译准得多。
  2. **再按游戏术语词汇表逐词翻译**剩下的部分（Cape→斗篷、Hair→发型…）。

翻不出来的：**不保留英文**，退回该物品的中文部位名（partZh）；脚本会打印没收录的词，
方便继续补词表。就地更新 app/src/main/assets/codex/items.json 的 nameZh 字段。
"""

import io
import json
import os
import re
import sys

PROJECT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ITEMS = os.path.join(PROJECT, 'app', 'src', 'main', 'assets', 'codex', 'items.json')
ZH = os.path.join(PROJECT, 'data', 'zh-names', 'zh-names.json')

GLOSSARY = {
    # 部位 / 分类
    "cape": "斗篷", "hair": "发型", "hair accessory": "发饰", "mask": "面具",
    "outfit": "套装", "necklace": "项链", "held": "手持", "prop": "道具",
    "shoes": "鞋", "music": "乐谱", "instrument": "乐器", "emote": "表情",
    "wing buff": "光翼", "blessing": "祝福", "heart": "爱心", "candle": "蜡烛",
    "challenge": "挑战", "quest": "任务", "ultimate": "终极", "seasonal": "季节",
    "elder": "长老", "spirit": "先祖", "friend": "好友", "spell": "魔法",
    "face accessory": "面饰", "hairpin": "发夹", "earring": "耳环", "scarf": "围巾",
    "pendant": "挂坠", "accessory": "配饰", "dye": "染料", "color": "颜色",
    "hat": "帽子", "mask ": "面具", "hair style": "发型", "style": "样式",
    "outfit ": "套装", "robe": "长袍", "pants": "裤子", "dress": "裙子",
    # 地点 / 季节
    "isle": "晨岛", "isle of dawn": "晨岛", "prairie": "云野", "daylight prairie": "云野",
    "forest": "雨林", "hidden forest": "雨林", "valley": "霞谷", "valley of triumph": "霞谷",
    "wasteland": "暮土", "golden wasteland": "暮土", "vault": "禁阁",
    "vault of knowledge": "禁阁", "eden": "暴风眼", "eye of eden": "暴风眼",
    "home": "遇境", "aviary": "云巢", "sanctuary": "圣岛", "sanctuary island": "圣岛",
    "little prince": "小王子", "aurora": "极光", "nesting": "巢穴",
    "season": "季节", "season of": "季", "remembrance": "追忆", "assembly": "集会",
    "belonging": "归属", "gratitude": "感恩", "lightseekers": "追光", "rhythm": "音韵",
    "enchantment": "魔法", "dreams": "梦想", "prophecy": "预言", "flight": "飞行",
    "abyss": "深渊", "performance": "表演", "shattering": "破碎", "passage": "通道",
    "moments": "时刻", "revival": "复兴", "nine colored deer": "九色鹿",
    # 常见修饰 / 生物
    "white bird": "白鸟", "butterfly": "蝴蝶", "flower": "花", "fire": "火",
    "snow": "雪", "winter": "冬季", "summer": "夏季", "spring": "春季", "autumn": "秋季",
    "star": "星星", "moon": "月亮", "sun": "太阳", "cloud": "云", "rain": "雨",
    "bird": "鸟", "fish": "鱼", "crab": "螃蟹", "jellyfish": "水母", "whale": "鲸鱼",
    "dragon": "龙", "fox": "狐狸", "rabbit": "兔子", "cat": "猫", "dog": "狗",
    "bear": "熊", "deer": "鹿", "wolf": "狼", "owl": "猫头鹰", "duck": "鸭子",
    "manta": "蝠鲼", "krill": "冥龙", "dark": "黑暗", "light": "光",
    "gold": "金色", "golden": "金色", "silver": "银色", "red": "红", "blue": "蓝",
    "green": "绿", "yellow": "黄", "purple": "紫", "pink": "粉", "black": "黑",
    "brown": "棕", "orange": "橙", "grey": "灰", "gray": "灰", "white": "白",
    # 动作 / 表情
    "wave": "挥手", "bow": "鞠躬", "clap": "鼓掌", "dance": "跳舞", "point": "指",
    "laugh": "大笑", "cry": "哭", "yawn": "打哈欠", "sit": "坐下", "sleep": "睡觉",
    "hug": "拥抱", "kiss": "飞吻", "salute": "敬礼", "shrug": "耸肩", "nod": "点头",
    "applaud": "鼓掌", "cheer": "欢呼", "stretch": "伸懒腰", "spin": "旋转",
    "thinking": "思考", "angry": "生气", "sad": "难过", "scared": "害怕",
    "frightened": "受惊", "confused": "困惑", "proud": "自豪", "shy": "害羞",
    "handstand": "倒立", "backflip": "后空翻", "cartwheel": "侧手翻",
    "crab walk": "螃蟹走", "sneeze": "打喷嚏", "cough": "咳嗽", "float": "漂浮",
    "stance": "姿势", "call": "呼唤", "warp": "瞬移", "confetti": "彩纸",
    "marching": "行进", "jolly": "快活", "flourishing": "繁花", "anxious": "焦虑",
    "sparkler": "烟花", "balance": "平衡", "scarred": "伤痕", "radiance": "光辉",
    # 人物称谓（先祖名里常见）
    "prophet": "先知", "dancer": "舞者", "warrior": "战士", "performer": "表演者",
    "pianist": "钢琴家", "cellist": "大提琴手", "whisperer": "低语者",
    "memory": "记忆", "parent": "父母", "alchemist": "炼金术士", "voyager": "旅人",
    "outcast": "弃儿", "drifter": "流浪者", "refugee": "难民", "shipwright": "造船匠",
    "bellmaker": "铸钟匠", "worshiper": "礼拜者", "catcher": "捕捉者",
    "charmer": "驯服者", "hunter": "猎手", "gatherer": "采集者", "hermit": "隐士",
    "elder ": "长老", "guide": "向导", "ancestor": "祖先", "prince": "王子",
    "princess": "公主", "queen": "女王", "king": "国王", "child": "孩子",
    # 物件
    "umbrella": "伞", "table": "桌子", "chair": "椅子", "lamp": "灯", "lantern": "灯笼",
    "banner": "旗帜", "flag": "旗", "hammock": "吊床", "swing": "秋千",
    "tea set": "茶具", "bonfire": "篝火", "campfire": "篝火", "torch": "火把",
    "pot": "锅", "jar": "罐子", "basket": "篮子", "book": "书", "scroll": "卷轴",
    "bell": "铃铛", "drum": "鼓", "harp": "竖琴", "guitar": "吉他", "piano": "钢琴",
    "flute": "笛子", "horn": "号角", "contrabass": "低音提琴", "ukulele": "尤克里里",
    "xylophone": "木琴", "shelf": "架子", "rack": "架子", "bed": "床", "rug": "地毯",
    "fence": "栅栏", "plant": "植物", "tree": "树", "statue": "雕像", "mirror": "镜子",
    "clock": "时钟", "camera": "相机", "paint": "颜料", "kite": "风筝",
    "balloon": "气球", "boat": "船", "tent": "帐篷", "cushion": "坐垫", "pillow": "枕头",
    # 其他常见词
    "special": "特殊", "small": "小", "large": "大", "big": "大", "long": "长",
    "short": "短", "first": "第一", "second": "第二", "third": "第三", "five": "五",
    "left": "左", "right": "右", "up": "上", "down": "下", "front": "前", "back": "后",
    "space": "空间", "shared": "共享", "beginnings": "初始", "face": "面",
    "1": "一", "2": "二", "3": "三", "4": "四", "5": "五", "6": "六", "7": "七",
    "8": "八", "9": "九", "0": "零",
    # 第二轮补充（按"还没收录的高频词"补的）
    "trail": "足迹", "waving": "招手", "pioneer": "先驱", "crying": "哭泣",
    "boogie": "摇摆", "duet": "二重奏", "duets": "二重奏",
    "moomin": "姆明", "around": "环绕", "high": "高", "double": "双",
    "bubbles": "泡泡", "cyan": "青色", "poster": "海报",
    "piggyback": "背背", "hacky": "踢毽", "sack": "麻袋",
    "wipe": "擦拭", "brow": "眉", "faint": "晕倒", "courageous": "勇敢",
    "look": "看", "shiver": "发抖", "hide": "躲", "seek": "寻找",
    "shocked": "震惊", "apologize": "道歉", "come": "来", "no": "不",
    "thanks": "谢谢", "furniture": "家具", "perch": "栖枝",
    "blue bird": "蓝鸟", "divining": "占卜", "wise": "智慧",
    "grandparent": "祖辈", "indifferent": "冷淡", "so": "如此",
    "confetti": "彩纸", "anxious": "焦虑", "waving hand": "招手",
    "crying baby": "哭泣", "duet dance": "双人舞",
}

DROP_WORDS = {
    "the", "of", "a", "an", "and", "with", "in", "on", "for", "to", "s",
    "version", "pre", "old", "new", "beta", "unused", "placeholder", "level", "lv",
}

PHRASES = sorted([k for k in GLOSSARY if " " in k], key=len, reverse=True)


def load_spirit_names():
    """先祖官方中英名：英文（小写）→ 中文"""
    if not os.path.exists(ZH):
        return {}
    with io.open(ZH, encoding='utf-8') as handle:
        data = json.load(handle)
    spirits = data.get('spirits', {}) if isinstance(data, dict) else {}
    pairs = {}
    for english, chinese in spirits.items():
        if english and chinese:
            pairs[english.strip().lower()] = chinese.strip()
    return pairs


def translate(name, spirit_names):
    text = (name or '').strip()
    if not text:
        return "", False
    lowered = text.lower()
    # 1) 先换先祖名（长的优先，避免短名先吃掉长名的一部分）
    for english in sorted(spirit_names, key=len, reverse=True):
        if english and english in lowered:
            lowered = lowered.replace(english, spirit_names[english])
    # 2) 再按词汇表逐短语替换
    for phrase in PHRASES:
        if phrase in lowered:
            lowered = lowered.replace(phrase, GLOSSARY[phrase])
    # 3) 剩下的逐词
    unknown = []
    out = []
    for token in re.split(r"[^0-9A-Za-z\u4e00-\u9fa5]+", lowered):
        if not token:
            continue
        if re.search(r"[\u4e00-\u9fa5]", token):
            out.append(token)
        elif token in DROP_WORDS:
            continue
        elif token in GLOSSARY:
            out.append(GLOSSARY[token])
        else:
            unknown.append(token)
    return "".join(out), bool(unknown)


def main():
    spirit_names = load_spirit_names()
    with io.open(ITEMS, encoding='utf-8') as handle:
        items = json.load(handle)

    translated = 0
    fallback = 0
    unknown_words = {}
    for item in items:
        zh, has_unknown = translate(item.get('nameEn') or '', spirit_names)
        if zh and not has_unknown:
            item['nameZh'] = zh
            translated += 1
        else:
            part = (item.get('partZh') or '').strip()
            zh_text = zh or ''
            cn = re.compile(r"[\u4e00-\u9fa5]")
            if cn.search(part):
                item['nameZh'] = part
            elif cn.search(zh_text):
                item['nameZh'] = zh_text
            else:
                # 部位名和翻译结果都没有中文：退回该物品分类的中文名，绝不留英文
                item['nameZh'] = GLOSSARY.get((item.get('type') or '').lower(), "物品")
            fallback += 1
            for word in re.split(r"[^0-9A-Za-z]+", (item.get('nameEn') or '').lower()):
                if word and word not in GLOSSARY and word not in DROP_WORDS:
                    unknown_words[word] = unknown_words.get(word, 0) + 1

    with io.open(ITEMS, 'w', encoding='utf-8') as handle:
        handle.write(json.dumps(items, ensure_ascii=False, separators=(',', ':')))

    print('先祖中英对照    : %d 条' % len(spirit_names))
    print('物品总数        : %d' % len(items))
    print('完全翻出来的    : %d (%.0f%%)' % (translated, 100.0 * translated / max(1, len(items))))
    print('用部位名兜底的  : %d' % fallback)
    print('')
    print('样例（英文 → 中文）:')
    for item in items[:4] + items[700:712]:
        print('   %-32s → %s' % ((item.get('nameEn') or '')[:32], item.get('nameZh') or '(空)'))
    print('')
    print('还没收录的高频词:')
    for word, count in sorted(unknown_words.items(), key=lambda kv: -kv[1])[:30]:
        print('   %-22s %d' % (word, count))
    return 0


if __name__ == '__main__':
    sys.exit(main())
