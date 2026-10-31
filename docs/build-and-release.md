# 构建、混编与发版

> 本文从 CLAUDE.md 抽离。**编译打包、动 Kotlin 代码、发新版本时必读。**

## 构建命令

仓库里**没有提交 `gradle/wrapper/gradle-wrapper.jar`**（只有一个孤立的 `gradle-wrapper.properties`），所以 `./gradlew` 用不了，必须直接调本机 Gradle：

```bash
JAVA_HOME="D:/green/jdks/jdk-17.0.8" /d/green/Gradle/dists/gradle-7.6.4/bin/gradle <任务> --no-daemon --offline
```

- `--offline` 是必要的：联网时 gradle-intellij-plugin 会去 GitHub 查最新版本，国内网络下抛 `getHeaderField("Location") must not be null`（不致命，但很吵）。
- 必须用 JDK 17，gradle-intellij-plugin 1.13.2 + 平台 2022.3 在 JDK 8 上跑不起来。
- **换机器或清了缓存后，`--offline` 会先失败一次**：Kotlin 编译器自己的 classpath（`kotlin-gradle-plugin`、`kotlin-script-runtime` 等）不在缓存里就没法离线解析。去掉 `--offline` 跑一次 `compileJava` 把它们拉下来（首次约 4 分钟），之后就一直能离线。本机 `GRADLE_USER_HOME` 是 `D:\green\Gradle\repository`，不是默认的 `~/.gradle`。

常用任务：

| 任务 | 用途 |
|---|---|
| `compileJava` | 只编译，最快的语法校验 |
| `buildPlugin` | 打包，产物在 `build/distributions/TreeInfoTip-Notes-<版本>.zip` |
| `verifyPlugin` | 校验 plugin.xml 配置 |
| `runIde` | 起沙箱 IDE 实测（沙箱目录是仓库根的 `idea-sandbox/`） |
| `runPluginVerifier -PverifierIdeVersions=IU-2022.3.2,IU-2026.2` | 跨版本兼容性检查 |

`runPluginVerifier` 在国内网络下要额外传三个属性，否则会卡在下载上：

```bash
JAVA_HOME="D:/green/jdks/jdk-17.0.8" /d/green/Gradle/dists/gradle-7.6.4/bin/gradle runPluginVerifier --no-daemon \
  -PverifierLocalPaths="D:/app/JetBrains/IntelliJ IDEA 2026.2.1" \
  -PverifierRuntimeDir="D:/app/JetBrains/IntelliJ IDEA 2026.2.1/jbr" \
  -PverifierVersion=1.410
```

- `verifierLocalPaths` 直接指向本机已装的 IDE，绕开 `ideVersions` 每个版本 1~2GB 的下载。传了它就不再看 `verifierIdeVersions`。
- `verifierRuntimeDir` 不传的话插件会联网下 JBR，直接 `Connection timed out`。随便给个 JDK 17 或 IDE 自带的 `jbr` 目录都行。
- `verifierVersion` 默认是 `latest`，解析它要访问 `api.github.com`，同样超时。给具体版本号（`1.410`）就改从 maven 拉，走 aliyun 镜像。
- **这个任务不能加 `--offline`**：verifier CLI 本身要从 maven 取。报告在 `build/reports/pluginVerifier/<IDE build>/`，跑一轮约 13 分钟。

**没有测试代码**：`src/test` 目录不存在，`gradle test` 会通过但什么都没跑。要验证纯算法逻辑（比如路径匹配优先级），可以把逻辑抄成临时的单文件 Java，用 `java Xxx.java` 跑断言，跑完删掉。

改完代码**必须 `runIde` 实测**。`plugin.xml` 里 action 注册写错、或者引用了新版本已删除的 `AllIcons` 字段，都是启动期抛异常（历史事故：`AllIcons.Actions.Menu_paste` 在 2026.2 被移除，右键菜单整个不可用）。编译通过说明不了任何问题；另外目录树只在**重绘时**才会应用新样式。

## 源码布局与 Kotlin 混编

主体是 Java（`src/main/java`），5.1.1 起额外开了 `src/main/kotlin`，两边可以互相调用，`compileKotlin` 先跑、`compileJava` 后跑。**老的 `.java` 不需要动**，新代码想写 Kotlin 就直接写。

已经是 Kotlin 的：`gui/ColorsUtils.kt`、`gui/IconsUtils.kt`、`gui/entity/IconEntity.kt`（都是没有 Lombok、没有 Swing 继承的叶子类）。

混编的硬约束：

- **绝对不能把 kotlin-stdlib 打进插件**。IDE 自带一份，重复会冲突。靠两条配合实现：`gradle.properties` 里 `kotlin.stdlib.default.dependency=false`，`build.gradle` 里 stdlib 写成 `compileOnly`。打包后 `TreeInfoTip-Notes/lib/` 下只应该有插件 jar、`javatuples`、`searchableOptions` 三个，出现 `kotlin-stdlib-*.jar` 就是配置漏了。
- **Kotlin 语言版本要压到最低支持 IDE 那一档**。`since-build=223` 对应 2022.3.0，它自带 Kotlin 1.7.21，所以 `apiVersion`/`languageVersion` 都锁 `"1.7"`。不锁的话用到新 stdlib 才有的函数编译期不报错，要到用户的老 IDE 上炸 `NoSuchMethodError`。
- Java 要调 Kotlin，签名得手动配：`object` 里的函数加 `@JvmStatic`，字段加 `@JvmField`，**被 Java `switch` case 标签用到的常量必须是 `const val`**（`ColorsUtils.COLOR_TEXT_COLOR_NAME` 就是，写成 `@JvmField val` 直接编译不过）。


## 版本与发版流程

**只要改动了功能代码，就必须升一个新版本、本地打包、推送远端仓库。** 纯文档、注释、CI 配置的改动不需要升版本。

版本号按改动大小定：

- **补丁位**（5.0.3 → 5.0.4）：bug 修复、文案调整、小的行为修正
- **次版本位**（5.0.4 → 5.1.0）：新增菜单项、新增匹配方式等新功能
- **主版本位**（6.x → 7.0.0）：配置文件格式或文件名变了、旧版插件读不了的大改动。7.0.0 就是这么来的：配置从平铺改成嵌套树、文件名换成 `DirectoryV7.xml`、属性名也全改短了（见 `docs/v7-migration.md`）。6.0.0 也是一次主版本，当时是为抽离路径前缀换成了 `DirectoryV6.xml`。

每次发版按顺序做完：

1. 改 `gradle.properties` 的 `pluginVersion`。这是**唯一版本来源**，`build.gradle` 的 `version` 和打包产物名都由它驱动。
2. 同步 `src/main/resources/META-INF/plugin.xml` 的 `<version>`。打包时 `patchPluginXml` 会覆盖它，但源码里也要一致，免得看着对不上。
3. 在 `plugin.xml` 的 `<change-notes>` 顶部加本版条目，中英文各一份，沿用现有格式。
4. 本地跑 `buildPlugin`，然后解包核对 jar 里的 `plugin.xml` 版本号、change-notes 和新增的 class 都在。
5. 删掉 `build/distributions/` 下的旧版本 zip，避免混淆。
6. 提交并 `git push origin master`。

要发 GitHub Release 就推 tag：`git tag v5.0.4 && git push origin v5.0.4`。`release.yml` 会校验 tag 去掉 `v` 之后必须等于 `pluginVersion`，不一致直接失败。上传 JetBrains Marketplace 需要 `JETBRAINS_MARKETPLACE_TOKEN` 环境变量，仓库里不存 token。
