# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

沟通、注释与文档一律用中文。

## 项目概览

TreeInfotip 是一个 IntelliJ 平台插件，给项目目录树的节点加备注、颜色、图标、悬浮提示、删除线和自定义显示名。所有配置都存在**项目根目录的 `DirectoryV3.xml`** 里，不用 IDE 的持久化设置。

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
| `buildPlugin` | 打包，产物在 `build/distributions/TreeInfotip-Notes-<版本>.zip` |
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

- **绝对不能把 kotlin-stdlib 打进插件**。IDE 自带一份，重复会冲突。靠两条配合实现：`gradle.properties` 里 `kotlin.stdlib.default.dependency=false`，`build.gradle` 里 stdlib 写成 `compileOnly`。打包后 `TreeInfotip/lib/` 下只应该有插件 jar、`javatuples`、`searchableOptions` 三个，出现 `kotlin-stdlib-*.jar` 就是配置漏了。
- **Kotlin 语言版本要压到最低支持 IDE 那一档**。`since-build=223` 对应 2022.3.0，它自带 Kotlin 1.7.21，所以 `apiVersion`/`languageVersion` 都锁 `"1.7"`。不锁的话用到新 stdlib 才有的函数编译期不报错，要到用户的老 IDE 上炸 `NoSuchMethodError`。
- Java 要调 Kotlin，签名得手动配：`object` 里的函数加 `@JvmStatic`，字段加 `@JvmField`，**被 Java `switch` case 标签用到的常量必须是 `const val`**（`ColorsUtils.COLOR_TEXT_COLOR_NAME` 就是，写成 `@JvmField val` 直接编译不过）。


**只要改动了功能代码，就必须升一个新版本、本地打包、推送远端仓库。** 纯文档、注释、CI 配置的改动不需要升版本。

版本号按改动大小定：

- **补丁位**（5.0.3 → 5.0.4）：bug 修复、文案调整、小的行为修正
- **次版本位**（5.0.4 → 5.1.0）：新增菜单项、新增匹配方式等新功能
- **主版本位**（5.x → 6.0.0）：`DirectoryV3.xml` 格式不兼容、需要用户重新配置的大重构

每次发版按顺序做完：

1. 改 `gradle.properties` 的 `pluginVersion`。这是**唯一版本来源**，`build.gradle` 的 `version` 和打包产物名都由它驱动。
2. 同步 `src/main/resources/META-INF/plugin.xml` 的 `<version>`。打包时 `patchPluginXml` 会覆盖它，但源码里也要一致，免得看着对不上。
3. 在 `plugin.xml` 的 `<change-notes>` 顶部加本版条目，中英文各一份，沿用现有格式。
4. 本地跑 `buildPlugin`，然后解包核对 jar 里的 `plugin.xml` 版本号、change-notes 和新增的 class 都在。
5. 删掉 `build/distributions/` 下的旧版本 zip，避免混淆。
6. 提交并 `git push origin master`。

要发 GitHub Release 就推 tag：`git tag v5.0.4 && git push origin v5.0.4`。`release.yml` 会校验 tag 去掉 `v` 之后必须等于 `pluginVersion`，不一致直接失败。上传 JetBrains Marketplace 需要 `JETBRAINS_MARKETPLACE_TOKEN` 环境变量，仓库里不存 token。

## 架构

### 数据流

```
DirectoryV3.xml（项目根目录）
   ↓ PluginStartupActivity.runActivity（postStartupActivity）
   ↓ XmlFileUtils.loadXmlFile → XmlStorage.parsing
XmlStorage.XML_STORAGE_LIST：每个 Project 一份 List<XmlEntity> 内存缓存
   ↓ TreesUtils.getMatchPath(virtualFile, project)
   ↓ TreesStyle.setStyle(presentation, entity, name)
PresentationData：图标 / locationString / tooltip / presentableText / 文字色 / 删除线 / 背景色
```

装饰有**两个入口**，都汇到 `TreesStyle.setStyle`：`TreeOnlyTextProvider`（`treeStructureProvider`）和 `IgnoreViewNodeDecorator`（`projectViewNodeDecorator`）。所以改渲染只需要改 `TreesStyle` 一个地方。

`XmlChangeListener` 挂了 PSI 树监听，XML 一变就重新 `parsing`，手改文件也能立刻生效。

### 匹配优先级（`TreesUtils.getMatchPath`）

一条 `<tree>` 有两种形态，命中优先级从高到低：

| 规则 | 写法 | 命中范围 |
|---|---|---|
| 路径规则 | `<tree path="/a/B.java" .../>` | 路径全等的那一个文件或目录，优先级最高 |
| 目录级类型规则 | `<tree path="/src/main/java" extension="java" .../>` | 该目录及各级子目录下的 `.java`；多条同时命中时 `path` 更长的赢 |
| 全项目类型规则 | `<tree extension="java" .../>` | 整个项目的 `.java`，只做兜底 |

扩展名规则只作用于文件，目录节点不参与。路径末尾多写的 `/` 由 `trimTrailingSlash` 归一化，只写 `/` 等于整个项目。

### 右键菜单

菜单类都在 `action/` 下，注册在 `plugin.xml` 的 `TreeInfotip.MenuGroup` 里，挂到 `ProjectViewPopupMenu`。

针对单个节点的菜单统一用 `XmlFileUtils.runActionType(event, callback)` 模板：它把选中节点转成相对路径，查缓存决定走 `onModifyPath`（已有配置）还是 `onCreatePath`（还没有）。`ActionDescriptionText` 是最标准的例子，加新菜单直接照抄。

**`runActionType` 只匹配路径规则**（见 `isPathRule`）。带 `extension` 的类型规则一条管一批文件，不能被单节点菜单顺手改掉，所以 `ActionDescriptionExtension` 不走这个模板，自己读 `VIRTUAL_FILE_ARRAY`；也因此**类型规则的删除入口只在它自己内部**——「清除全部设置」是按路径匹配的，碰不到类型规则。

### 写 XML

所有属性写入都走 `XmlStorage.setAttributeIfNotEmpty`：值为空就把已存在的属性删掉（`XmlTag.setAttribute(name, null)` 是删除语义）。`XmlStorage.tree()` 解析时会把缺失属性归一成 `""`，直接回写就会在文件里堆出 `extension="" icon=""` 这类噪音，所以不要绕过这个方法。

新增一个可配置属性要同时改四处：`XmlEntity` 加字段、`XmlStorage` 加常量并在 `tree()` / `modify()` / `create()` 三处登记、`TreesStyle.setStyle` 应用到 `PresentationData`、最后加对应 action 并在 `plugin.xml` 注册。

### 回调注册表

`TreesStyle.ListenerStyle`、`XmlFileUtils.ListenerSave`、`PluginStartupActivity.ListenerRun` 都是 `ConcurrentHashMap<Object, Callback>` 静态注册表，用于配置变化时刷新工具窗口。它们**只 put 从不 remove**，key 一般传监听方自己的实例。

### 插件身份与全局 id（5.2.0 起）

本仓库是 `Link-Kou/intellij-treeInfotip` 的复刻。JetBrains 不会在原作者不配合的情况下把已有的 Marketplace 条目转给新 vendor，所以复刻版只能作为**另一个插件**发布，于是要和原版划清四套互不相干的命名空间：

| 名字 | 在哪 | 撞了会怎样 |
|---|---|---|
| `<id>` = `com.github.yc556.treeinfotip` | `plugin.xml` | IDE 的更新检查按 id 去 Marketplace 查，沿用原 id 会被原版的构建静默"更新"掉 |
| `<name>` = `TreeInfotip Notes` | `plugin.xml` | Marketplace 条目名要唯一。**只能用拉丁字符**，写中文上传会被拒，详见下面一节 |
| `intellij.pluginName` = `TreeInfotip-Notes` | `build.gradle` | 它是 zip 根目录名，也就是装完后 `plugins/<这个名字>/`；和原版同名时后装的直接覆盖前一个的安装目录 |
| action / group / toolWindow / notificationGroup 的 id | `plugin.xml` | 这些注册表是 IDE 全局的，重名会被拒绝注册。全部加了 `TreeInfotip` 前缀 |

`group 'com.github.yc556'`（`build.gradle`）只是 Gradle 坐标，纯装饰，和上面四个都无关。

**工具窗口 id 同时就是侧边栏上显示的文字**：平台按 `toolwindow.stripe.<id，空格换成下划线>` 去插件自己的资源包（没声明 `<resource-bundle>` 时是 `messages.IdeBundle`）找标题，找不到就**直接拿 id 当标题**（`com.intellij.toolWindow.ToolwindowKt#getStripeTitleSupplier` → `BundleBase.messageOrDefault`）。所以 id 带空格是合法且常见的（平台自己就有 `Version Control`、`Event Log`），现在这两个窗口的 id 是 `TreeInfotip 备注` 和 `TreeInfotip XML`，靠这条回退直接当标题用，不用建资源包。改 id 的代价是 `workspace.xml` 里记的窗口位置和大小会重置一次。

action id 不对用户显示（菜单文字来自 `text=` 属性），改名只会丢掉用户自己配的快捷键——这些 action 本来就没有默认快捷键，可以忽略。

`OldPluginConflictNotifier` 在 `PluginStartupActivity.runActivity` 末尾检测旧 id `com.linkkou.plugin.intellij.assistant` 还在不在，一个 IDE 会话只弹一次通知。这一条同时覆盖两种人：装着原版的，和从本插件 5.1.x 升上来的（那些构建用的就是这个旧 id）。

两个插件同时装着时的实际情况，别搞反：

- 共用 `DirectoryV3.xml` **不是冲突点**，反而是迁移免费的原因。读是各自解析进自己类加载器里的 `XmlStorage.XML_STORAGE_LIST`；写只发生在用户点菜单时，同一时刻只有一个在写，另一个靠 PSI 监听重新解析。
- 真正的代价是每次重绘算两遍，而且两个装饰入口的执行顺序不定，**旧版跑在后面时会 `clearText()` 掉新版才有的覆盖显示名称等设置**。
- 检测到冲突时**不要**顺手跳过自己的装饰：跳过等于把渲染完全交给旧版，用户必然看不到新特性；两边都跑最坏也就是退化成旧版的效果，是弱优于跳过的。
- 也没有用 `<incompatible-with>`（2022.3 确实支持，`XmlReader` 解析进 `RawPluginDescriptor.incompatibilities`，`PluginSetBuilder` 执行）。它直接让插件不加载，太硬；而且它和 `com.intellij.pluginReplacement` 互斥——插件都不加载了，自然也注册不了那个 EP。

### Marketplace 的描述符校验（5.3.1 起）

上传 zip 时 Marketplace 会校验 `plugin.xml`，不过这一关就传不上去。踩过的两条：

- **首次上传时 `<name>` 只能用拉丁字符**。放行的是字母、数字、空格和 `.,+_-/:()#'&[]|`，中日韩文字直接判"包含无效字符"。5.2.0 设的 `TreeInfotip 目录树备注` 就是这么被拒的（5.3.1 改成 `TreeInfotip Notes`）。Plugin Verifier 1.393 的发布说明把这条写死成 "Plugin name must be in Latin characters"。
- **`<description>` 要以拉丁字符开头、正文至少 40 字**。正文里的中文没问题，现在开头那句 `TreeInfotip plugin for IntelliJ IDEs.` 正好满足，**改描述时别把中文段落挪到最前面**，emoji 放开头也会被拒。

**条目建起来之后名字可以带中文**。作者另一个插件 `yc-2018/intellij-sql-heading-folding`（本机 `D:\myData\intellij-sql-heading-folding`）是实证：2026-08-09 用纯英文 `SQL Heading Folding` 在网页首发建条目，08-13 的 1.0.4 改成 `SQL Heading Folding / SQL 标题折叠`，之后一路发到 1.1.8 都带着中文名。所以顺序是**先用拉丁名把条目建出来，再改中英文名发新版本**，不是"中文永远不行"。

到底是"只有首次创建条目才校验名字"还是"网页上传校验、API 上传不校验"，这一个案例分不出来——它把两个变量一起变了（首次→更新、网页→API）。想改中文名就照它走过的路来：CI 里 `./gradlew publishPlugin`，token 从 `JETBRAINS_MARKETPLACE_TOKEN` 环境变量读。本仓库 `release.yml` 已经有这个步骤（挂在 `env.JETBRAINS_MARKETPLACE_TOKEN != ''` 后面），只差在 GitHub 仓库 Secrets 里加上 token。

Marketplace 的插件名始终从 `plugin.xml` 读，网页后台改不了，所以改名只能靠上传新版本。

顺带还有几条软约束（来自 Marketplace 的命名与审核指南）：名字里不能出现 `JetBrains` 或其他 JetBrains 品牌词，不建议带 `Plugin`、`Support`、`Integration` 这类词，不能用 emoji，长度上限 60、建议控制在 30 以内。

### Plugin Verifier 的 API 校验（5.3.2 起）

描述符那关过了之后还有第二关。**结论是 `Compatible` 也照样会被打回**：5.3.1 上传后 Marketplace 回了一封 "The Plugin Verifier found issues with this update"（工单 #9122161，条目 34046、更新 1160283、`approve:false`），而报告里并没有任何解析失败的类或方法，`Compatible` 那行下面跟的是：

```
5 usages of scheduled for removal API and 2 usages of deprecated API. 2 usages of internal API
```

**内部 API（`@ApiStatus.Internal`）是必须清掉的**，用了就是明确违规；待删除和已废弃的严格说算警告，但一起清掉最省事，免得再来一封。5.3.2 清掉的 9 处对应关系：

| 原来用的 | 换成 |
|---|---|
| `PluginManagerCore.getPlugin(PluginId) != null`（internal） | `isPluginInstalled(id) && !isDisabled(id)` |
| `PluginManagerConfigurable`（类本身 internal） | 按 configurable id `"preferences.pluginManager"` 找 |
| `ContentFactory.SERVICE`（两处） | `ContentFactory.getInstance()` |
| `com.intellij.ui.ColorChooser` | `ColorChooserService.getInstance().showDialog(...)` |
| `ReadAction.run(ThrowableRunnable)` | `ApplicationManager.getApplication().runReadAction(Runnable)` |
| 覆盖 `ProjectViewNodeDecorator.decorate(PackageDependenciesNode, ...)` | 直接删（2022.3 起是 default 方法，覆盖没作用） |
| 覆盖 + 自调 `TreeStructureProvider.getData(Collection, String)` | 直接删（样式在 `modify` 里已经全量应用过） |

#### 选替代 API 的方法：两份 classpath 一起比

难点在于 `since-build=223` 要求替代品在 **2022.3.2 就存在**，而它同时得在 **verifier 的目标版本上不是 internal / 不带删除标记**。只看一头必然踩坑（我第一版把 6 参 `showDialog` 选进去，就是只扫了 2026.2 没扫 2022.3）。两份 lib 目录：

- 2022.3.2（编译基线）：`/d/green/Gradle/repository/caches/modules-2/files-2.1/com.jetbrains.intellij.idea/ideaIU/2022.3.2/<hash>/ideaIU-2022.3.2/lib` —— 注意有一层 hash 目录，`ls -d .../ideaIU/*/` 找不到，用 `find ... -name app.jar` 定位。
- 2026.2.1（目标）：`/d/app/JetBrains/IntelliJ IDEA 2026.2.1/lib`

用 `javap -v -cp "$(ls *.jar | tr '\n' ';')" <全限定类名>` 把方法声明和紧跟其后的 `RuntimeInvisibleAnnotations` 配对着看，就能判定每个重载的状态。已经查清的几条，省得再查一遍：

- `PluginManager.getInstance()` 和 `findEnabledPlugin()` 在 2026.2 都是 internal，**不能**当 `PluginManagerCore.getPlugin` 的替代品；`isPluginInstalled` / `isDisabled` 两个版本都干净。
- `PluginManagerConfigurable.getId()` 在两个版本都返回 `"preferences.pluginManager"`（`javap -c` 里能直接看到 `ldc // String preferences.pluginManager`），所以按 id 找 configurable 是稳的。**别改成 `ActionManager` 执行内置 action**：那个 action 的 id 2022.3 是 `WelcomeScreen.Plugins`、2026.2 已经改成 `ShowPlugins`，跨版本对不上。
- **`ColorChooserService.showDialog` 的两个重载废弃状态是反的**：不带 `Project` 的 6 参版在 **2022.3 就是 `@Deprecated(forRemoval)`**、到 2026.2 反而干净；带 `Project` 的 7 参版两个版本都干净。所以要用 7 参那个（`SelectColorIconsView` 为此加了 `Project` 构造参数）。`project` 形参可以传 `null`——2022.3 的形参没标 `@NotNull`，2026.2 的 Kotlin 实现也只对 `parent` 和 `listeners` 做 `checkNotNullParameter`。

## 已知约束

- `plugin.xml` 声明 `since-build="223"`，和编译平台 2022.3.2（`gradle.properties` 的 `platformVersion`）对齐，所以下限不再是「谎报」。但**上限没有**：`build.gradle` 里 `updateSinceUntilBuild = false` 是刻意的，不能写 `until-build`，否则新版 IDE 装不上。代价是新 IDE 删掉的 API 只能在运行期暴露——反射拿到的 `AllIcons` 字段名就可能凭空消失（历史事故见上）。
- `sourceCompatibility = targetCompatibility = 17`。**不要提到 21**：`platformVersion=2022.3.2` 自带的 JBR 是 17，21 的字节码在 `runIde` 沙箱和用户机器上都加载不了；真要上 21 得先把 `platformVersion` 拉到 2025.x，那会一并撞上 `ContentFactory.SERVICE` 这类已标记删除的 API。
- **新版平台的 EDT 不再隐式持有读锁**，Swing 监听器里直接碰 PSI 或索引会抛 `Read access is allowed from inside read-action only`（`ThreadingAssertions.assertReadAccess`）。2022.3 上不报，2024.1 起报——`NoteTreeView` 的双击跳转就是这么在 2026.2 上炸的（5.3.0 修）。补法是自己包一层读操作，用 `ApplicationManager.getApplication().runReadAction(Runnable)`：**不能用报错信息里推荐的 `WriteIntentReadAction`**（那个类 2024.1 才有，`since-build=223` 编不过），**也不能用 `ReadAction.run(ThrowableRunnable)`**（那个重载已废弃，Plugin Verifier 会报出来，5.3.2 换掉）。`TreesUtils.Navigation` 里 `findDirectory`/`findFile` 和 `selectPsiElement` 包在同一个 read action 里，因为后者内部还要再读一次 PSI 拿 `VirtualFile`。
- 侧边栏（`NoteTreeView`）的路径失效检查和类型图标都在 `buildNode` 里算一次、缓存在 `MyTreeNode` 的 `missing` / `icon` 字段上，**不要挪进渲染器**：`customizeCellRenderer` 每帧对每个可见行都会调一次，碰 VFS 和 `FileTypeManager` 太贵。代价是在 IDE 外面删文件不会自动变红，靠双击根节点重建列表刷新；查存在性走 `TreesUtils.findProjectFile`（`refreshIfNeeded=false`），宁可漏报也不要把好路径误标成失效。另外 `root.add(...)` 不发 model 事件，`DefaultTreeModel.reload()` 必须在加完子节点**之后**调（原代码是先 reload 再 add，新节点得等下一次重绘才出来）。
- 图标下拉框没有「不设置」选项（`IconsUtils.ICONS` 纯反射 `AllIcons` 生成），所以颜色/图标对话框一点确定就必然写入一个 `icon` 属性。这是既有行为。
- **量图标尺寸不能用裸 JVM 反射 `getIconWidth()`**。没有 `Application` 时 `CachedImageIcon` 加载不了真图，会退化成 16×16 的空图标，量出来「全都是 16×16」，看着像没问题其实什么都没量到。要量真实尺寸就直接读平台 jar 里 SVG 根标签声明的 `width` / `height`（2022.3 共 5894 张去重 SVG，其中 1221 张不是 16×16；`AllIcons` 暴露的 1080 个字段里有 50 个长边超过 16，最大 48×48）。要验缩放逻辑就自己造假 `Icon`（只重写 `getIconWidth` / `getIconHeight`）去打 `IconsUtils.fit`，不依赖 `Application`。
- 缩图标别用 `IconUtil.resizeSquared`：它的比例**只按宽算**（`IconUtil$4.paintIcon` 里 `ratio = size / source.getIconWidth()`），`AllIcons` 里有 `2×19`、`32×15`、`18×22` 这类非正方形的，按宽算会把 `2×19` 放大成 `16×152`。要按 `max(宽, 高)` 自己算倍率交给 `IconUtil.scale(icon, null, factor)`（`OBJ_SCALE` 相对倍率，平台会叠在 DPI 缩放之上）。
- `XmlEditorToolWindow` 的「抽离路径前缀」还是空的 TODO；「格式化」和「清理空属性」是正则实现，属性值里出现 `<` / `>` 时不安全。
- `verifyPluginConfiguration` 会报 "The Kotlin configuration specifies apiVersion=1.7 but since-build='223' property requires apiVersion=1.7.0"，这是它按字符串比对的**误报**：真填 `1.7.0`，Kotlin 编译器直接报 `Unknown Kotlin version: 1.7.0`。这条告警忽略即可，`build.gradle` 里也写了注释。
- `XmlEntity` 是全项目唯一用 Lombok 的类（`@Data @Accessors(chain = true)`），**不要顺手改成 Kotlin**：Kotlin 的 `var` 生成 void setter，和同名的链式 `setXxx(): XmlEntity` 是 platform declaration clash，没法共存。要么照抄链式 setter（比 Lombok 还长），要么改掉 14 处调用点（`ActionDescriptionExtension` 和 `XmlStorage` 里都有一长串链式调用）。所以 Lombok 目前拿不掉。
- **`runIde` 起的 IU 沙箱没授权时，`postStartupActivity` 一个都不会跑**，包括本插件读 `DirectoryV3.xml` 的那次初始化。原因是未授权的 IU 会弹一个模态的 **Licenses** 窗口占住 EDT 的 modality state：`postStartupActivity` 没实现 `DumbAware` 时，`StartupManagerImpl.runPostStartupActivities` 走的是 `DumbService.unsafeRunWhenSmart(Runnable)`，模态对话框在就永远不触发（给它发 `WM_CLOSE` 也没用）。表现是沙箱能起、插件能加载、日志 0 个 ERROR，但插件像没装一样，而且沙箱常在启动约 2.5 分钟后自己退出。
  - `build.gradle` 的 `runIde` 里那个授权 javaagent 路径是 `/Applications/jetbra/fineagent.jar`，**只在作者的 macOS 上存在**，Windows 上就是这个现象。
  - 判断标记：日志里只有 background 类的启动活动（`IsUpToDateCheckStartupActivity`、`CodeWithMeCleanup` 这些在 +5s 出现），却没有任何 smart-mode 活动。要确认就用 `EnumWindows` 列一下那个 JVM 的可见窗口，会看到 `Licenses`。
  - 绕过办法：把要验的逻辑临时从别的入口调一次。`ProjectViewNodeDecorator.decorate` 不受 modality 和 dumb mode 限制，是现成的替代入口（验完记得撤掉）。
