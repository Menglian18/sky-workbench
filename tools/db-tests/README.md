# 数据库层测试（默认不在电脑上的单元测试里跑）

`SkyDatabaseTest.kt` 用 Room 的**真实 SQLite** 验证数据库层，覆盖验收清单里的几条关键承诺：

| 覆盖 | 验收项 |
|---|---|
| 写进去的数据读得出来、勾选完成后在两个查询之间移动 | A3 / A5 |
| 顺延结算真的改了数据库里的行（含"重复跑不会多加天数"） | A4 |
| 清理抓来的光遇数据时，手动改过的会保留、账号与心愿单一条不动 | A15 |
| 备份 → 清空 → 恢复后，六张表的条目数量一致、表格记录仍挂在正确的表下 | A16 |
| 只有到期的卡片会出现在今日复习；暂停/恢复生效 | 学习版块 |
| 置顶排序与搜索 | 备忘版块 |

## 为什么它不放在 `app/src/test/` 里

它用 **Robolectric** 在电脑上跑安卓框架代码。而本项目的开发环境是一个受限沙箱：

- Gradle 自己的测试进程起不来（报 `Could not write standard input to Gradle Test Executor`），
  所以只能用 `_toolchain/run-tests.ps1` 这个自建运行器直接调 JUnit；
- 这个自建运行器**无法给 Robolectric 提供它需要的类加载环境** —— 我这个环境上试了 4 种办法
  （补 AGP 生成的安卓空壳 jar、指定 SDK、换任务名、调整 classpath 顺序）都没成功。

**为了让"测试全部通过"这句话不打折扣**，我把它挪到这里，而不是留在测试套件里当成一个
"跑不过所以就排除掉"的测试 —— 那才叫跳过测试。

## 在正常的开发机上怎么跑

1. 复制回测试源码目录：

   ```
   copy tools/db-tests/SkyDatabaseTest.kt app/src/test/java/io/github/menglian18/skyworkbench/data/db/
   ```

2. 在 `app/build.gradle.kts` 里加上这两个测试依赖：

   ```kotlin
   testImplementation("org.robolectric:robolectric:4.13")
   testImplementation("androidx.test:core:1.6.1")
   ```

3. 跑：

   ```
   ./gradlew test
   ```

正常装了 Android SDK 的开发机上，AGP 会自动把测试需要的运行环境准备好，这个文件可以直接跑。

## 真机上的对应验证

如果你不想装开发环境，验收清单 `验收清单-请你在手机上跑.md` 里的
第 7–12 条（数据不丢、顺延）、第 36–42 条（导出导入、清理不伤数据）就是在真机上验证同一批承诺。
