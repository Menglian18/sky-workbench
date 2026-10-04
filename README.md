# 光遇工作台

开源仓库：<https://github.com/Menglian18/sky-workbench>

一个装在安卓手机上的个人生活 App：**不用登录、断网也能用、数据只存在手机本地**。

## 六个版块

| 版块 | 它解决什么 |
|---|---|
| **首页总览** | 打开就知道今天什么情况：待办、复习、复刻倒计时、最近备忘，一屏看完 |
| **今日计划** | 今天要做的事，做完打勾；没做完的**第二天自动跟过来** |
| **娱乐 · 光遇管家** | 复刻还剩几天、这物品要多少蜡烛、我换到没有；另有骰子/转盘/休息铃声 |
| **学习安排** | 把要背的丢进来，每天按遗忘节奏（FSRS v6）告诉你该复习哪些 |
| **数据备忘** | 生活文本（快递号、尺寸、证件号…）能搜能一键复制；数字表格能记能看趋势 |
| **数据与设置** | 6 套主题、字体三档、导出/导入数据、一键清理 |

## 数据与隐私

- **零登录、零账号、零广告 SDK**。
- 数据只存在这台手机上（本地 SQLite 数据库），不采集、不上报。
- 换手机靠「导出数据文件 / 导入数据文件」，一个文件搬走全部数据。
- **唯一会联网的地方**：抓取光遇的复刻与季节数据（一小时最多一次，抓不到就保留上次的数据）。
- 权限只申请：网络、震动。不要通讯录、位置、相机、麦克风。

## 自己编译

需要：

- JDK 17
- Android SDK：`platforms;android-35`、`build-tools;34.0.0`（`platform-tools` 可选）
- 编译插件：Gradle 8.7 + Android Gradle Plugin 8.6.1（仓库里带了 Gradle wrapper，不用另外装）

```bash
./gradlew assembleDebug      # 产出 app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease    # 正式包（需要自己准备签名，见下）
./gradlew test               # 跑单元测试
```

最低支持安卓 8.0（API 26），编译目标 35、运行目标 34。

### 打正式包

在项目根目录放一个 `keystore.properties`（**已被 .gitignore 排除，不会上传**）：

```properties
storeFile=../你的密钥.jks
storePassword=你的口令
keyAlias=你的别名
keyPassword=你的口令
```

没有这个文件时，`assembleDebug` 照常可用，只是打不出正式包。

### 说明：如果项目放在含中文的目录里

AGP 默认会因为"路径含非 ASCII 字符"而拒绝编译。解决办法是在 `gradle.properties` 里加一行：

```properties
android.overridePathCheck=true
```


## 数据来源与授权

| 内容 | 来源 | 授权 |
|---|---|---|
| 光遇复刻 / 季节数据 | [B站光遇WIKI（BWIKI）](https://wiki.biligame.com/sky/) | CC BY-NC-SA 4.0（**可免费用、须署名、禁止商用**） |
| 光遇物品 / 先祖 / 价格数据 | [SkyGame-Data](https://github.com/Skyfeelin/SkyGame-Data) | MIT |
| 本项目代码 | 本仓库 | Apache-2.0（见 `LICENSE`） |

- 光遇相关的中文名映射数据单独放在 `data/zh-names/`，并附它的来源与使用规矩，**不按代码的授权发布**。
- 本仓库**不包含任何游戏图片或美术素材**；《光·遇》的名称与素材版权归 thatgamecompany / 网易所有。
- 如果你要把本项目用于商业用途，请先替换掉所有来自 BWIKI 的数据（CC BY-NC-SA 禁止商用）。

## 致谢

- Google 官方示例 [android/nowinandroid](https://github.com/android/nowinandroid) 与 [android/architecture-samples](https://github.com/android/architecture-samples)（Apache-2.0）：架构与多主题写法参考。
- [open-spaced-repetition](https://github.com/open-spaced-repetition)：公开的 FSRS v6 复习算法规范。
- [Vico](https://github.com/patrykandpatrick/vico)（Apache-2.0）：趋势图。
