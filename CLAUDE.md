# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

沟通、注释与文档一律用中文。

## 项目概览

TreeInfoTip Notes 是一个 IntelliJ 平台插件，给项目目录树的节点加备注、颜色、图标、悬浮提示、删除线和自定义显示名。所有配置都存在**项目根目录的 `DirectoryV7.xml`** 里，不用 IDE 的持久化设置。

配置文件 7.0.0 起是**嵌套树**结构（一个 `<node>` 套下一层 `<node>`，每层 `path` 只写相对上一层那截），运行时**只认 V7**。开项目时若只有旧的 `DirectoryV6.xml` / `DirectoryV3.xml`，`XmlFileUtils.migrateIfNeeded` 按它的内容生成一份 V7 并发通知，**旧文件原样保留、不改名也不删**——细节见 `docs/v7-migration.md`。

## 分册文档：先看这张表

细节按主题拆在 `docs/` 下。这些文件**不会自动进上下文**，动手前按下表自己 Read，不要凭印象改：

| 文件 | 什么时候必须读 |
|---|---|
| `docs/platform-constraints.md` | **改任何 Swing / PSI / 线程相关代码之前**。读锁、导航、图标、跨版本 API 差异，全是踩过的坑和历史事故 |
| `docs/build-and-release.md` | 要编译打包、跑 `runPluginVerifier`；动 Kotlin 代码；**发新版本**（版本号规则和 6 步流程在里面） |
| `docs/v7-migration.md` | 改 `LegacyReader` / `NodePaths` / `NodeWriter` / `V7Migrator`，或碰嵌套结构的读写、老配置迁移逻辑 |
| `docs/tool-window.md` | 改 `NoteTreeView` / `MemberTreeView` / `HelpView` / `PsiCommentUtils` |
| `docs/publishing.md` | 改 `plugin.xml` 的 id / name / description，或要上传 Marketplace |

维护这份文档时**不要用 `@docs/xxx.md` 这种 import 写法**引用它们：那个语法会把整个文件递归展开进上下文，拆分就白做了。保持普通的路径提及。

## 三条红线

1. **改完代码必须 `runIde` 实测。** `plugin.xml` 里 action 注册写错、或者引用了新版本已删除的 `AllIcons` 字段，都是启动期抛异常（历史事故：`AllIcons.Actions.Menu_paste` 在 2026.2 被移除，右键菜单整个不可用）。编译通过说明不了任何问题；另外目录树只在**重绘时**才会应用新样式。本机沙箱目前起不来的原因见 `docs/platform-constraints.md` 末尾。
2. **只要改动了功能代码，就必须升一个新版本、本地打包、推送远端仓库。** 纯文档、注释、CI 配置的改动不需要升版本。版本号规则和发版步骤见 `docs/build-and-release.md`。
3. **没有测试代码。** `src/test` 目录不存在，`gradle test` 会通过但什么都没跑。要验证纯算法逻辑（比如路径匹配优先级），可以把逻辑抄成临时的单文件 Java，用 `java Xxx.java` 跑断言，跑完删掉。

## 构建命令

仓库里**没有提交 `gradle/wrapper/gradle-wrapper.jar`**，所以 `./gradlew` 用不了，必须直接调本机 Gradle：

```bash
JAVA_HOME="D:/green/jdks/jdk-17.0.8" /d/green/Gradle/dists/gradle-7.6.4/bin/gradle <任务> --no-daemon --offline
```

- `--offline` 是必要的：联网时 gradle-intellij-plugin 会去 GitHub 查最新版本，国内网络下抛 `getHeaderField("Location") must not be null`。
- 必须用 JDK 17。本机 `GRADLE_USER_HOME` 是 `D:\green\Gradle\repository`，不是默认的 `~/.gradle`。

| 任务 | 用途 |
|---|---|
| `compileJava` | 只编译，最快的语法校验 |
| `buildPlugin` | 打包，产物在 `build/distributions/TreeInfoTip-Notes-<版本>.zip` |
| `verifyPlugin` | 校验 plugin.xml 配置 |
| `runIde` | 起沙箱 IDE 实测（沙箱目录是仓库根的 `idea-sandbox/`） |
| `runPluginVerifier` | 跨版本兼容性检查，**参数很多且不能加 `--offline`**，照 `docs/build-and-release.md` 来 |

换机器或清了缓存后 `--offline` 会先失败一次，处理办法同样在 `docs/build-and-release.md`。

## 架构

### 数据流

```
DirectoryV7.xml（项目根目录）
   ↓ PluginStartupActivity.runActivity（postStartupActivity）
   ↓ XmlFileUtils.migrateIfNeeded（只有 V7 不存在、又找得到 V6/V3 时才转一次）
   ↓ XmlFileUtils.loadXmlFile → XmlStorage.parsing（先序递归，把每层 path 累加成完整路径）
XmlStorage.XML_STORAGE_LIST：每个 Project 一份 List<XmlEntity> 内存缓存
   ↓ TreesUtils.getMatchPath(virtualFile, project)
   ↓ TreesStyle.setStyle(presentation, entity, name)
PresentationData：图标 / locationString(note) / tooltip / presentableText(label) / 文字色(color) / 删除线(strike) / 背景色(bg)
```

装饰有**两个入口**，都汇到 `TreesStyle.setStyle`：`TreeOnlyTextProvider`（`treeStructureProvider`）和 `IgnoreViewNodeDecorator`（`projectViewNodeDecorator`）。所以改渲染只需要改 `TreesStyle` 一个地方。

`XmlChangeListener` 挂了 PSI 树监听，XML 一变就重新 `parsing`，手改文件也能立刻生效。**只认 V7**：老文件在迁移那一刻读完就退休，之后再改它不触发重新解析。

### 匹配优先级（`TreesUtils.getMatchPath`）

内存里的 `XmlEntity.path` 一律是**完整路径**，所以匹配逻辑完全不知道嵌套这回事。7.1.0 去掉 `extension` 后只剩一种规则：**完整路径全等**即命中。同一路径写了多条时，列表里靠前的赢（列表就是文件里的先后顺序）。路径末尾多写的 `/` 由 `trimTrailingSlash` 归一化。

**嵌套不改变命中结果**：只有完整路径相同的规则才互相竞争，而它们归堆后都落在同一个父 `<node>` 下、相对顺序原样保留。所以「置顶」只需挪到**同层兄弟**的最前面，不用像平铺时代挪到整个文件头部。

### 右键菜单

菜单类都在 `action/` 下，注册在 `plugin.xml` 的 `TreeInfotip.MenuGroup` 里，挂到 `ProjectViewPopupMenu`。

针对单个节点的菜单统一用 `XmlFileUtils.runActionType(event, callback)` 模板：它把选中节点转成相对路径，查缓存决定走 `onModifyPath`（已有配置）还是 `onCreatePath`（还没有）。`ActionDescriptionText` 是最标准的例子，加新菜单直接照抄。所有菜单都走这个模板（7.1.0 去掉 `extension` 后，唯一的例外 `ActionDescriptionExtension` 也删了）。

### 写 XML

节点定位、新建、拆分、清理空壳全在 `NodeWriter`；`XmlStorage` 的 `create/modify/remove/moveToTop` 调它。属性写入走 `NodeWriter.setIfNotEmpty`：值为空就把已存在的属性删掉（`XmlTag.setAttribute(name, null)` 是删除语义）。`XmlStorage.parsing` 解析时会把缺失属性归一成 `""`，直接回写就会在文件里堆出 `icon="" color=""` 这类噪音，所以不要绕过这个方法。

一条嵌套特有的不变量：**只有 `path` 的纯容器节点**删光底下规则后要跟着消失（`NodeWriter.deleteRule` → `prune` 自下而上清空壳）。

新增一个可配置属性要同时改这些地方：`XmlEntity` 加字段、`XmlStorage` 加常量并补进 `SETTING_ATTRIBUTES`、`toEntity()` / `writeSettings()` 两处登记、`V7Migrator.attributes()` 和 `LegacyReader.tree()`（老属性名映射）各补一条、`TreesStyle.setStyle` 应用到 `PresentationData`、加对应 action 并在 `plugin.xml` 注册。**还有说明文档**：`XmlFileUtils.XML_FOOTER` 的注释和 `HelpView.attributes()` 的参数列表都要补一条。新项目第一次加备注时由 `XmlFileUtils.createXmlFile` 写出 `XML_TEMPLATE`（`XML_HEADER` + 空 `<trees>` + `XML_FOOTER`），footer 那段注释列全参数、嵌套写法和命中优先级——这文件躺在项目根目录，用户迟早点开它。**XML 注释里不能出现连续两个减号**这条约束仍然有效。

### 回调注册表

`TreesStyle.ListenerStyle`、`XmlFileUtils.ListenerSave`、`PluginStartupActivity.ListenerRun` 都是 `ConcurrentHashMap<Object, Callback>` 静态注册表，用于配置变化时刷新工具窗口。它们**只 put 从不 remove**，key 一般传监听方自己的实例。
