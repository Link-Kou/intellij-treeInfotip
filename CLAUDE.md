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

新增一个可配置属性要同时改四处：`XmlEntity` 加字段、`XmlStorage` 加常量并在 `tree()` / `modify()` / `create()` 三处登记、`TreesStyle.setStyle` 应用到 `PresentationData`、最后加对应 action 并在 `plugin.xml` 注册。**还有第五处**：`XmlFileUtils.XML_TEMPLATE` 的注释和 `HelpView.attributes()` 的参数列表都要补一条，否则用户看到的说明会缺项。

新项目第一次加备注时由 `XmlFileUtils.createXmlFile` 写出 `XML_TEMPLATE`，`<trees>` 下面带一段注释列全九个参数和命中优先级（5.6.0 起，之前是个空 `<trees/>`）——这文件躺在项目根目录，用户迟早点开它，而 `presentableText`、`tooltipTitle` 这些参数名单看名字猜不全。**改这段注释时有四样东西不能出现**，都会被「TreeInfotip XML」窗口的「格式化」误伤：`<trees>` 和 `</trees>`（会被塞进换行）、带空格的 `<tree `（会被缩进四格）、行尾的 `>` 紧接下一行开头的 `<`（`>\s*<` 会被压成一个换行）；另外 XML 注释本身不允许出现连续两个减号。

### 侧边栏工具窗口（5.6.0 起三个 tab）

`plugin.xml` 里 `TreeInfotip 备注` 这个 `toolWindow` 的 `factoryClass` 指向 `NotesToolWindowFactory`，它建三个 `Content`：

| tab | 面板类 | 内容 |
|---|---|---|
| 文件成员（默认选中） | `MemberTreeView.createPanel(project)` | 当前编辑文件的方法 / 属性树，每项后面跟注释 |
| 目录备注 | `NoteTreeView.createPanel(project)` | `DirectoryV3.xml` 里的规则平铺一行一条 |
| 说明 | `HelpView.createPanel(project)` | 一页 HTML：菜单怎么用、九个参数、命中优先级 |

三个面板类都是**私有构造 + 静态 `createPanel`**（前两个 `extends Tree`，`HelpView` `extends JEditorPane`），自己套 `SimpleToolWindowPanel`（`setToolbar` + `setContent(new JBScrollPane(this))`）。`NoteTreeView` 5.5.0 起**不再是 `ToolWindowFactory`**，别再往它身上加 `createToolWindowContent`。三个 content 都 `setCloseable(false)`——关掉了没有入口再开。

**「说明」tab 用 `JEditorPane` 装一页 HTML**，不拼 Swing 控件：要的就是现成的标题、列表和等宽字体排版。三条必须做的：kit 用 `new HTMLEditorKitBuilder().withWordWrapViewFactory().build()`（**建完别再调 `setContentType`**，那会把 kit 换回 Swing 自带的、丢掉平台样式表）；覆盖 `getScrollableTracksViewportWidth()` 返回 `true`，正文才按侧边栏实际宽度重排；`setOpaque(false)` 露出 viewport 底色，否则深色主题下是一块白。Swing 的 HTML 停在 3.2，**不要写表格**（参数名加说明在这个宽度排不开，列会被压成竖着一串字），用 `ul` / `ol` / `tt`；`pre` 例子每行压在 35 字符左右，因为横向滚动条是 `HORIZONTAL_SCROLLBAR_NEVER`、超出直接裁掉。正文分成 `intro()`、`menu()`、`tabs()`、`attributes()`、`priority()`、`example()` 几段拼，**菜单文字要和 `plugin.xml` 里 `TreeInfotip.MenuGroup` 的 `text=` 一致，参数列表要和 `XmlStorage` 的常量对得上**，改那两处时这里也要改。工具栏只有一个「打开 DirectoryV3.xml」：`XmlFileUtils.getXmlFile(project)` 拿缓存（可能为 `null`，那就 `Messages` 提示去右键菜单加第一条，**不顺手建空文件**），`getVirtualFile()` 包在 `runReadAction` 里，`new OpenFileDescriptor(project, file, 0).navigate(true)` 在读操作外面。

**「文件成员」不自己解析语法，借 IDE 的结构视图取节点**：`FileEditor.getStructureViewBuilder()` → 转 `TreeBasedStructureViewBuilder` → `createStructureViewModel(null)`（不传 `Editor`，不需要跟随光标）→ `getRoot()` → 递归 `TreeElement.getChildren()`。这么做 Java、TS / JS / TSX / JSX、Kotlin、Python、Go 全是白拿的，而且**不用在 `plugin.xml` 里加任何 `<depends>`**。三条硬约束：

- `StructureViewModel extends Disposable`，**必须在 `finally` 里 `dispose()`**，否则漏掉它内部挂的监听。
- 不是 `TreeBasedStructureViewBuilder` 的 builder（图片、二进制之类的自定义编辑器）拿不到节点，直接给一句「这类文件没有结构信息」，没有别的办法。
- **不能用 `LanguageStructureViewBuilder`**：`INSTANCE` 字段在 2026.2 已废弃，`getInstance()` 在 2022.3 还不存在，两头都不占。走 `FileEditor` 这条路两个版本都干净。

**判断一个节点是方法还是属性，不认任何具体语言的 PSI 类**（认了就得加 `<depends>`，而且每多支持一种语言就得改一次）：往上翻实现类的父类链、每一层再翻它的接口，只比**简单名**——含 `Method` / `Function` / `Constructor` 算方法，含 `Field` / `Property` / `Variable` / `Constant` 算属性，兜底看显示文字里有没有 `(`。两头都不沾的归 `KIND_OTHER`，而 **OTHER 永远显示**：宁可多显示几行，也不要因为认不出类别就把整个类连着它的方法一起藏掉。另外**过滤只作用在叶子上**（`children.isEmpty()`），内层节点（类、接口）被滤掉的话它下面的成员就成了孤儿。

拉杆的层数**同时是构建深度和展开深度**：`build()` 到了 `maxDepth` 就不再递归，建完整棵树全展开。上限 10 层，再深在这个宽度的侧边栏里已经没法看。节点数另有 `MAX_NODES = 3000` 的上限，到了就停下并在末尾补一行灰字提示——几千行的压缩 js / 生成代码结构树能有上万个节点，全建出来再全展开会把 EDT 卡住。

刷新只在**切编辑器**（`FileEditorManagerListener.FILE_EDITOR_MANAGER` 的 `selectionChanged`）和**点刷新按钮**时发生，没挂 PSI 变化监听：边打字边重建整棵树太贵。`connect(project)` 把连接挂在项目上，项目关掉自动断开，不用自己 dispose。

### 读注释（`PsiCommentUtils`）

`PsiCommentUtils.read(psiElement)` 是「文件成员」每项后面那段注释的唯一来源，严格四级、命中一级就不往下找：**文档注释（`/**`）→ 多行注释（`/*`）→ 同行尾部的单行注释 → 紧贴在上方的连续单行注释**（一段空白里出现两个及以上换行就算空行，直接断开）。

**刻意只认平台自带的 `PsiComment`，靠注释文本开头的记号分类**。`PsiDocComment`、`JSDocComment` 这些都在各自的语言插件里，本插件不声明依赖，直接引用会在没装那些插件的 IDE 上 `NoClassDefFoundError`。代价是判断不了语言层面的语义，收益是一套逻辑覆盖所有语言（`//`、`#`、`--` 都当单行注释认）。

收集上方注释要**分两步**，因为不同语言把注释挂在不同地方：Java 的 JavaDoc、Kotlin 的 KDoc 是方法元素**自己的第一个子节点**，而 JS / TS / Go 里多半是方法的**前一个兄弟**。所以先扫自己的头部子节点，一条都没有再用 `PsiTreeUtil.prevLeaf` 逐个叶子往前走。往前走时**必须用 `PsiTreeUtil.getParentOfType(leaf, PsiComment.class, false)` 把叶子抬回整条注释**——JSDoc 在 PSI 里是复合元素，直接拿叶子只能拿到 `/**` 这几个字符。

**但这两步都得先经过 `carrier(element)` 往上抬一层**（5.5.1 加的）。结构视图给的元素不一定是注释挂靠的那一层，TS / TSX 的箭头函数组件是最典型的反例：

```
/** 承运商招募报名审核工作台。 */
const CarrierRecruitRegPage: React.FC = () => {}
```

结构视图给的是 **`JSFunctionExpression`（箭头函数）**，JSDoc 挂在整条 `JSVarStatement` 上，中间隔着 `const CarrierRecruitRegPage: React.FC =` 一串 token。老逻辑在 `leadingComments` 第二步碰到第一个非注释叶子（`=`）就 `break`，所以这三种写法一条注释都读不出来：`const X = () => {}`（箭头函数）、`const x = {...}`（对象字面量变量）、多行 JSDoc + 箭头函数。

`carrier` 往上走的条件是**「当前元素就在父节点的开头」**（`startsParent`），层数上限 `MAX_LIFT = 4`，走到 `PsiFile` 停。「在开头」的判定是从当前元素往前逐个叶子走（`prevLeaf`），走出父节点的起始偏移量就算通过：

- **注释一律跳过**，它正是要找的东西，隔了几行也不影响判定。少了这一条，JSDoc 作为语句第一个子节点的那种挂法会被多行 JSDoc 自己的换行判成跨行，白抬一趟；反过来把「遇到注释」当成「已经到开头」也不行，那会抬过一条本属于当前元素的同行注释，把它丢掉。
- 其余实质 token **只要和当前元素之间夹了换行就判否**（`crossedLine` 标记）。夹了换行又夹着实质代码，说明父节点是更大的结构（类、代码块、多行对象字面量），它的注释不属于当前成员。Java 里类的第一个方法就是靠这条过不了——`class X {` 的花括号和方法之间必然有换行。
- 逐叶子走而不是切 `getText()`：父节点是个类的时候要把整个类的源码拼成字符串，太贵。压缩过的 js 整个文件只有一行，靠 `MAX_WALK = 64` 兜住，走太远就不再判断。

已知的误报是挤在一行里的对象字面量 `{a: 1, b: 2}`，属性会抬到字面量本身，宁可多显示一句也不要少显示。

`clean()` 里 `@param` / `@return` 这类标签行单独攒一份：正文有内容就只要正文，正文全是标签才退回去用它们，总比显示空白好。显示长度上限 `MAX_LENGTH = 120`。

### 回调注册表

`TreesStyle.ListenerStyle`、`XmlFileUtils.ListenerSave`、`PluginStartupActivity.ListenerRun` 都是 `ConcurrentHashMap<Object, Callback>` 静态注册表，用于配置变化时刷新工具窗口。它们**只 put 从不 remove**，key 一般传监听方自己的实例。

### 插件身份与全局 id（5.2.0 起）

本仓库是 `Link-Kou/intellij-treeInfotip` 的复刻。JetBrains 不会在原作者不配合的情况下把已有的 Marketplace 条目转给新 vendor，所以复刻版只能作为**另一个插件**发布，于是要和原版划清四套互不相干的命名空间：

| 名字 | 在哪 | 撞了会怎样 |
|---|---|---|
| `<id>` = `com.github.yc556.treeinfotip` | `plugin.xml` | IDE 的更新检查按 id 去 Marketplace 查，沿用原 id 会被原版的构建静默"更新"掉 |
| `<name>` = `TreeInfotip Notes / 目录树备注` | `plugin.xml` | Marketplace 条目名要唯一。**首次建条目时只能用拉丁字符**，条目建出来之后才能改成中英文（5.4.0 起就是中英文），详见下面一节 |
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

- **`<name>` 只能用拉丁字符**。放行的是字母、数字、空格和 `.,+_-/:()#'&[]|`，中日韩文字直接判"包含无效字符"。5.2.0 设的 `TreeInfotip 目录树备注` 就是这么被网页上传拒掉的（5.3.1 改成 `TreeInfotip Notes`）。Plugin Verifier 1.393 的发布说明把这条写死成 "Plugin name must be in Latin characters"。
- **`<description>` 要以拉丁字符开头、正文至少 40 字**。正文里的中文没问题，现在开头那句 `TreeInfotip plugin for IntelliJ IDEs.` 正好满足，**改描述时别把中文段落挪到最前面**，emoji 放开头也会被拒。

#### 中文名到底行不行：分上传通道，不分首次/更新（5.4.0 实测）

**这条校验属于 Plugin Verifier CLI 自己的 structure 检查，和"是不是首次建条目"无关。** 5.4.0 把 `<name>` 改成 `TreeInfotip Notes / 目录树备注` 之后本地跑 `runPluginVerifier`（1.410），直接判整个包无效、连 API 检查都不跑：

```
Plugin is invalid in build\distributions\TreeInfotip-Notes-5.4.0.zip:
  Invalid plugin descriptor 'plugin.xml'. Name 'TreeInfotip Notes / 目录树备注' contains invalid characters.
  Only the following characters are allowed: letters, digits, spaces, and .,+_-/:()#'&[]|
Scheduled verifications (0)
```

条目 34046 早就存在、这一版是"更新"，照样被拒——所以之前"条目建起来之后名字就能带中文"的说法是错的，真正的分界是**上传通道**：

| 通道 | 跑不跑这条 structure 检查 | 中文名 |
|---|---|---|
| 网页后台上传 zip | 跑 | 被拒（5.2.0 的实测） |
| 本地 `runPluginVerifier` | 跑 | 直接判 invalid，API 检查全部跳过（5.4.0 的实测） |
| `./gradlew publishPlugin`（Marketplace API） | 不跑 | 能过 |

API 通道的实证是作者另一个插件 `yc-2018/intellij-sql-heading-folding`（本机 `D:\myData\intellij-sql-heading-folding`）：`<name>` 是 `SQL Heading Folding / SQL 标题折叠`，CI（`.github/workflows/build-release.yml:54`）**只有 `./gradlew publishPlugin`、没有 `runPluginVerifier`**，就这么从 1.0.4 一路发到了 1.1.8。

所以想要中英文名，配套约束是硬的：

- **必须走 CI 的 `publishPlugin`，不能用网页上传 zip。** 本仓库 `release.yml` 已经有这个步骤（挂在 `env.JETBRAINS_MARKETPLACE_TOKEN != ''` 后面），只差在 GitHub 仓库 Secrets 里加 token。
- **本地想跑 `runPluginVerifier` 查 API 问题，得先把 `<name>` 临时改成纯拉丁再打包**，跑完再改回来。5.4.0 就是这么验的：拉丁名下 verifier 给出 `Compatible`、无任何 deprecated / internal 用法，然后把中文名改回去重新打包发布。别省这一步——5.3.1 就是因为 API 问题被打回的，而中文名会让 verifier 一个 API 都不查。

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
- **新版平台的 EDT 不再隐式持有读锁**，Swing 监听器里直接碰 PSI 或索引会抛 `Read access is allowed from inside read-action only`（`ThreadingAssertions.assertReadAccess`）。2022.3 上不报，2024.1 起报——`NoteTreeView` 的双击跳转就是这么在 2026.2 上炸的（5.3.0 修）。补法是自己包一层读操作，用 `ApplicationManager.getApplication().runReadAction(Runnable)`：**不能用报错信息里推荐的 `WriteIntentReadAction`**（那个类 2024.1 才有，`since-build=223` 编不过），**也不能用 `ReadAction.run(ThrowableRunnable)`**（那个重载已废弃，Plugin Verifier 会报出来，5.3.2 换掉）。`TreesUtils.Navigation` 里 `findDirectory`/`findFile` 和 `selectPsiElement` 包在同一个 read action 里，因为后者内部还要再读一次 PSI 拿 `VirtualFile`。**要往外带值就用一元数组 + 语句块**：`runReadAction(() -> x[0] = f())` 写成表达式会在 `Runnable`、`Computable<T>`、`ThrowableComputable<T, E>` 三个重载之间歧义（报「对 runReadAction 的引用不明确」），必须写成 `runReadAction(() -> { x[0] = f(); })`。
- **`PsiElement.navigate(true)` 本身就要读 PSI，不能当成「导航动作」放在读操作外面**（5.5.1 修）。它内部先走 `EditSourceUtil.getDescriptor(element)` 算目标位置，那一步会调 `getTextOffset()`；TS 的箭头函数上 `JSFunctionExpressionImpl.findNameIdentifier` 为了求偏移量要一路 `getParentSkipParentheses` → `getGreenStubTree` 翻上去，必炸 `Read access is allowed from inside read-action only`。5.5.0 的 `MemberTreeView` 就是被「navigate 会开编辑器、动光标，必须在读操作外面调」这个错判坑的——那句话只对 `OpenFileDescriptor.navigate` 成立。正确写法是拆两半：在 read action 里把 `PsiFile.getVirtualFile()` 和 `getTextOffset()` 取进一元数组，出来之后 `new OpenFileDescriptor(project, file, offset).navigate(true)`，和 `NoteTreeView.navigateToRule` 同一套。顺带 `canNavigate()` 也不必再问了，拿不到 `VirtualFile` 就等于不能跳。
- 侧边栏（`NoteTreeView`）的路径失效检查和类型图标都在 `buildNode` 里算一次、缓存在 `MyTreeNode` 的 `missing` / `icon` 字段上，**不要挪进渲染器**：`customizeCellRenderer` 每帧对每个可见行都会调一次，碰 VFS 和 `FileTypeManager` 太贵。代价是在 IDE 外面删文件不会自动变红，靠工具栏的「刷新」按钮重建列表（5.4.1 之前是双击根节点）；查存在性走 `TreesUtils.findProjectFile`（`refreshIfNeeded=false`），宁可漏报也不要把好路径误标成失效。另外 `root.add(...)` 不发 model 事件，`DefaultTreeModel.reload()` 必须在加完子节点**之后**调（原代码是先 reload 再 add，新节点得等下一次重绘才出来）。
- 侧边栏「目录备注」**不做真实目录树，就是平铺一行一条**（用户确认过）。规则是稀疏的，一条 `/src/main/java/a/b/C.java` 在树里要建五层空目录才够挂上它，翻起来比一行一条还慢。既然不做树，`setRootVisible(false)` + `setShowsRootHandles(false)`，5.4.1 那个「备注列表（双击刷新）」的根节点也就没用了（5.5.0 去掉，刷新挪到工具栏）。**渲染器里非 `MyTreeNode` 的分支直接 `return`**，别再往根节点上写文字。
- 侧边栏「目录备注」**路径已失效的规则一律排在最前面**（5.5.0 起）：`reload()` 先把节点分到 `missing` / `alive` 两个 list，先加 missing 再加 alive。项目树上已经没有它们的节点了，不排上去用户就得自己往下翻。同一趟顺手把失效的实体存进 `missingEntities` 字段给「清除失效路径」用——**这个字段必须在 action 里先拷一份再删**，因为 `XmlStorage.removeByTag` 存盘会触发 `ListenerSave` → `reload()`，把它清空。
- **XML 里标签的先后顺序是有语义的**，所以「置顶」是真的改文件不是列表排序：`TreesUtils.getMatchPath` 在同优先级时让先遇到的那条赢。PSI 没有「移动子节点」的 API，`XmlStorage.moveToTop(Project, List)` 的做法是 `rootTag.addSubTag(tag, true)`（插到最前，**返回的是新拷贝**，原 `tag` 还指着老位置）然后 `tag.delete()`，**顺序不能反**——先删就没有源可拷了。**置顶支持 Ctrl 多选**（5.6.0 起，之前只挪了一条），两条硬要求：**倒着遍历**这批实体（每条都插到最前，正着走会把它们整体翻个面），而且**整批必须在同一个 `WriteCommandAction` 里做完**——每条各开一次写操作会各触发一次 `saveFileXml` 和重新解析，后面那些 `XmlEntity.tag` 全部失效，只有第一条能成。已经整批贴在最前面且顺序没变时返回 0，调用方不白刷一次列表。选中项**要按行号从上往下走**（`isRowSelected` + `getPathForRow`）而不是读 `getSelectionPaths()`，后者给的是点选的先后顺序，和列表里看到的上下顺序不一定一致。
- **列表里的 `AnAction` 一律不覆盖 `update()`**（5.5.0 起）。`getActionUpdateThread()` 的默认值 2022.3 是 EDT、2026.2 是 BGT，在 `update()` 里读 Swing 的选中状态跨版本不安全。所以这些 action 永远是启用的，空选中的情况在 `actionPerformed` 里处理（那里一定在 EDT 上），用 `Messages` 弹一句。删除类动作都要先 `Messages.showDialog(...)` 确认，**默认焦点放「取消」**（`defaultOptionIndex=1`），改的是用户项目里的 `DirectoryV3.xml`，不可逆。
- 右键菜单**自己继承 `PopupHandler` 覆盖 `invokePopup(Component, int, int)`**，不用 `PopupHandler.installFollowingSelectionTreePopup(JTree, ActionGroup, String)`（5.7.0 换掉）。那个方法的判据反编译出来是 `tree.getPathForLocation(x, y) != null && Arrays.binarySearch(tree.getSelectionRows(), tree.getRowForLocation(x, y)) > -1`，两个调用**都认横坐标**，点在标签文字右边的空白处返回 -1 / null，菜单弹不出来——用户报的就是「右键只能作用在文字上」。自己接的写法是 `addMouseListener(new PopupHandler() { invokePopup → rowAt(y) → 必要时 setSelectionRow → ActionManager.createActionPopupMenu(place, group).getComponent().show(comp, x, y) })`，基类只负责 `isPopupTrigger()` 的跨平台差异（Windows 松开时触发、X11 / macOS 按下时），所以换掉没有副作用；`PopupHandler` 本身、`createActionPopupMenu(String, ActionGroup)`、`ActionPopupMenu.getComponent()` 在 2022.3.2 和 2026.2.1 上都干净（顺带 `installFollowingSelectionTreePopup` 的 4 参重载 2022.3 已废弃、2026.2 已删除，只剩 3 参的）。**选中不用自己管**：`Tree.MyMouseListener` 用的是 `getClosestPathForLocation`，右键落在没选中的行上会改选中、落在已选中的行上保留整批多选，而且它在 `Tree` 构造里就注册了，比这里装的先跑。判行号要用 `getClosestRowForLocation(0, y)`（基本只看 y）**再用 `getRowBounds(row)` 卡一下范围**，不卡的话点最后一行下方的大片空白会算成最后一行。**别换成 `PopupHandler.installPopupHandler(...)`**，那几个重载在新版平台上全带删除标记，Plugin Verifier 会报出来。工具栏和右键菜单的 `place` 字符串（`TreeInfotipNoteListToolbar` 等）只用于 action 事件溯源，**不是全局注册的 id**，不受重名限制。
- **同键的重复规则只标灰不静默删**（5.7.0 起）。「身份」是 `TreesUtils.ruleKey(entity)`：`trimTrailingSlash(path) + '\0' + extension.trim().toLowerCase()`，**归一化必须和 `getMatchPath` 一致**（匹配用 `equalsIgnoreCase`、`/src/` 和 `/src` 是同一条），用 `\0` 分隔是因为它不可能出现在属性值里，不会把两条不同规则拼成同一个键。键相同的第二条**不是「部分生效」而是完全没用**——三类规则都是先到先得（路径规则全等即 `return`，目录级是 `rulePath.length() > matchedScopeLength` 严格大于，全项目只在没命中过时兜底），哪怕它多配了先来那条没有的属性也读不到。`reload()` 里用一个 `HashSet<String> seen` 边走边 `!seen.add(key)` 判定，**登记要在 `buildNode` 之前**：没有文字的规则不进列表，但它照样占着这条键。状态存在 `MyTreeNode.shadowed` 上（和 `missing` 一样建节点时算好，判据只看配置本身、不碰 VFS），渲染器里**`missing` 优先于 `shadowed`**。`shadowedEntities` 收的是**全部**重复规则、含没进列表的那些，「清理重复规则」（`AllIcons.Actions.Copy`）删它们时在生效的第一条一定留着，所以项目树的显示前后完全不变；和 `missingEntities` 一样**必须在 action 里先拷一份再删**。不在 `XmlStorage.parsing` 里去重、也不自动改文件：`DirectoryV3.xml` 在用户项目根目录、会进版本库，插件不背着人重写它。顺带一条：从右键菜单改一条重复路径的备注**会让两条一起变**，因为 `XmlFileUtils.runActionType` 绑定的是**全部**路径匹配，`ActionDescriptionText.onModifyPath` 对每一条都调了 `XmlStorage.modify`。
- 侧边栏列表的显示规则**是不对称的**（5.4.0 起，5.4.1 收紧）：`label()` 先取用户写的文字（`title` → `presentableText` 兜底），两个都空时按「在项目树上看不看得见效果」分岔——**看得见的返回空串**，`buildNode` 直接返回 `null` 不进列表（效果在项目树上本来就看得见，列表里却只有空白一行，认不出是哪条也点不动，而它的入口就在项目树的右键菜单上）；**路径已失效或被同键规则盖住的必须进列表**（后者 5.7.0 加的），没有文字就拿它配的路径当标题（普通规则）或 `*.扩展名 @ 生效范围`（类型规则），因为项目树上连节点都没有 / 压根不生效，列表是用户唯一能发现并清掉它的地方。只设了颜色/图标/删除线的规则就属于「没有文字」这一类，**按扩展名批量设置的规则也一样按这条走**（5.4.0 曾无条件给类型规则显示 `*.ts @ /src/x` 后缀，5.4.1 改成只在失效时显示）。因此 `buildNode` 里必须先算 `missing` 再决定跳不跳，`label(entity, typeRule, forceShow)` 三个参数都是必需的，第三个传的是 `missing || shadowed`。只写 `extension` 的全项目规则没有路径可查，永远算有效，所以它没写文字、也没被盖住时不进列表。
- 侧边栏双击**一定要有反应**（5.4.0 起）：`navigate()` 先自己查一次 VFS，能落到真实文件就 `TreesUtils.Navigation`，否则跳 `DirectoryV3.xml` 里这条规则所在的行。这里**不要看 `MyTreeNode.isMissing()`**——那个状态是建节点时算的，建完之后在 IDE 外面删文件不会刷新，照它判断会把已经失效的当成有效去跳，又变成没反应。跳 XML 的偏移量取自解析时存在 `XmlEntity.tag` 上的 `XmlTag`（`getTextOffset()`），行号一定对得上，不用自己在文本里找；`tag` 失效时退到 `XmlFileUtils.getXmlFile(project)` 的文件开头。读 PSI 那段要包在 `runReadAction` 里，`new OpenFileDescriptor(project, file, offset).navigate(true)` 要在读操作**外面**调。3 参构造和 `navigate(boolean)` 在 2022.3.2 和 2026.2.1 上都不带任何注解；同类里 `IntSupplier` 重载、`navigateInEditor`、`navigateIn` 在 2026.2 是 internal，别用。
- **「文件成员」是单击跳转、双击收缩**（5.5.1 起，5.5.0 是双击跳转）。双击跳转和树自带的展开切换撞在一起，双击一个有子节点的成员会「又收缩又定位」。但**不能简单改成单击就跳**：一次双击的第一下也是单击，照跳不误。做法是**有子节点的行等一小会儿**（`MAX_CLICK_WAIT = 200` 毫秒），期间来了第二下就 `Timer.stop()` 撤掉跳转；**叶子立刻跳**，它没有展开状态可切，白等只会显得迟钝。等待时间是 `Math.min(系统的 awt.multiClickInterval, 200)`：**系统值只当上限**，它在 Windows 上是 500ms，等满了点一个有子节点的成员要过半秒才动，手感像卡了一下（5.5.1 就是直接用系统值，5.6.0 压到 200ms）。两个值的含义本来就不同，系统值说的是「最长多久还算一次双击」，这里要的是「多久之后可以确定不会有第二下」，真正连着的两下基本落在 200ms 内。属性取不到或类型不对时按 `DEFAULT_CLICK_INTERVAL` 兜底。`javax.swing.Timer` 的回调本来就在 EDT 上（注意别 import 成 `java.util.Timer`）。另外**节点要从点击坐标取**（`getPathForLocation`）而不是读选中项：点在展开箭头或行尾空白处时它返回 `null`，正好把「点箭头收缩」和「点成员跳转」分开，读选中项的话点箭头会跳到上一次选中的成员上去。`reload()` 开头也要撤一次，整棵树都换了，排着队的跳转指向的是旧节点。
- **工具窗口和菜单图标不引 `AllIcons`，用仓库自带的 svg**（5.4.0 起）。理由和历史事故一样：反射不到的 `AllIcons` 字段会在新版 IDE 上凭空消失。两条约定：
  - **主题适配靠 `_dark` 文件名后缀，不写代码**。`icons/treeNote.svg`（`#6C707E`，浅色主题的标准图标灰）+ `icons/treeNote_dark.svg`（`#FFFFFF`），`plugin.xml` 里只声明 `/icons/treeNote.svg` 一个路径，平台自己去找 `_dark` 的那个。改图形时两个文件都要改。彩色图标（`icons/addNote.svg`）不需要 `_dark` 变体。工具窗口图标必须是单色 16×16，彩色 logo 缩到 16 会糊。
  - **SVG 根标签声明的 `width` / `height` 决定逻辑尺寸**，平台的 SvgLoader 按它算。外面拿来的图常常是 128×128，只改这两个属性成 16 就行，`viewBox` 不用动（`addNote.svg` 的 viewBox 还是 1024），不改会把菜单行整个撑高。
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
