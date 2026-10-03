# 光遇中文名映射数据

这个目录里的数据**不是代码**，它的使用规矩和本项目代码的授权（Apache-2.0）**不一样**，请分开看。

## 内容

- `zh-names.json`：光遇先祖/季节的中文名 ↔ 英文名对照表，由 `tools/build_codex.py` 在构建期从 B站光遇WIKI 提取。

## 来源

B站光遇WIKI（BWIKI）：<https://wiki.biligame.com/sky/>

- 中文名取自各季节页里的「先祖中文名 / 先祖英文名」字段
- 该站首页写明：若无特殊说明，内容按 **CC BY-NC-SA 4.0** 提供

## 使用规矩（重要）

| 允许 | 要求 |
|---|---|
| 免费使用、修改、再分发 | **必须署名**并给出协议链接 |
| 非商业用途 | **禁止商业用途**（不能卖钱、不能挂广告、不能内购） |
| 衍生作品 | 需以**相同协议**（CC BY-NC-SA 4.0）共享 |

协议全文：<https://creativecommons.org/licenses/by-nc-sa/4.0/>

**另外**：站点内所有游戏图片、游戏文案的版权属于游戏权利方（thatgamecompany / 网易），
**不在上面的 CC 协议范围内**，所以本项目**不打包任何游戏图片**。

## 如果你要商用

把 `zh-names.json` 以及 `app/src/main/assets/codex/` 里带 `nameZh` 字段的数据全部替换成你自己整理的名字
（这两处之外，物品与价格数据来自 [SkyGame-Data](https://github.com/Skyfeelin/SkyGame-Data)，是 MIT，可以商用）。
