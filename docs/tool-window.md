# 侧边栏工具窗口与注释读取

> 本文从 CLAUDE.md 抽离。**改 `NoteTreeView` / `MemberTreeView` / `HelpView` / `PsiCommentUtils` 时必读。**

## 侧边栏工具窗口（5.6.0 起三个 tab）

`plugin.xml` 里 `TreeInfoTip Notes` 这个 `toolWindow` 的 `factoryClass` 指向 `NotesToolWindowFactory`，它建三个 `Content`：

| tab | 面板类 | 内容 |
|---|---|---|
| 文件成员（默认选中） | `MemberTreeView.createPanel(project)` | 当前编辑文件的方法 / 属性树，每项后面跟注释 |
| 目录备注 | `NoteTreeView.createPanel(project)` | `DirectoryV7.xml` 里的规则按目录层级组织成一棵树（7.0.2 起），工具栏第二行数字 1-10 控制展开层数 |
| 说明 | `HelpView.createPanel(project)` | 一页 HTML：菜单怎么用、各个参数、命中优先级 |

三个面板类都是**私有构造 + 静态 `createPanel`**（前两个 `extends Tree`，`HelpView` `extends JEditorPane`），自己套 `SimpleToolWindowPanel`（`setToolbar` + `setContent(new JBScrollPane(this))`）。`NoteTreeView` 5.5.0 起**不再是 `ToolWindowFactory`**，别再往它身上加 `createToolWindowContent`。三个 content 都 `setCloseable(false)`——关掉了没有入口再开。

**「说明」tab 用 `JEditorPane` 装一页 HTML**，不拼 Swing 控件：要的就是现成的标题、列表和等宽字体排版。三条必须做的：kit 用 `new HTMLEditorKitBuilder().withWordWrapViewFactory().build()`（**建完别再调 `setContentType`**，那会把 kit 换回 Swing 自带的、丢掉平台样式表）；覆盖 `getScrollableTracksViewportWidth()` 返回 `true`，正文才按侧边栏实际宽度重排；`setOpaque(false)` 露出 viewport 底色，否则深色主题下是一块白。Swing 的 HTML 停在 3.2，**不要写表格**（参数名加说明在这个宽度排不开，列会被压成竖着一串字），用 `ul` / `ol` / `tt`；`pre` 例子每行压在 35 字符左右，因为横向滚动条是 `HORIZONTAL_SCROLLBAR_NEVER`、超出直接裁掉。正文分成 `intro()`、`menu()`、`tabs()`、`attributes()`、`priority()`、`example()` 几段拼，**菜单文字要和 `plugin.xml` 里 `TreeInfotip.MenuGroup` 的 `text=` 一致，参数列表要和 `XmlStorage` 的常量对得上**，改那两处时这里也要改。工具栏只有一个「打开 DirectoryV7.xml」：`XmlFileUtils.getXmlFile(project)` 拿缓存（可能为 `null`，那就 `Messages` 提示去右键菜单加第一条，**不顺手建空文件**），`getVirtualFile()` 包在 `runReadAction` 里，`new OpenFileDescriptor(project, file, 0).navigate(true)` 在读操作外面。

**「文件成员」不自己解析语法，借 IDE 的结构视图取节点**：`FileEditor.getStructureViewBuilder()` → 转 `TreeBasedStructureViewBuilder` → `createStructureViewModel(null)`（不传 `Editor`，不需要跟随光标）→ `getRoot()` → 递归 `TreeElement.getChildren()`。这么做 Java、TS / JS / TSX / JSX、Kotlin、Python、Go 全是白拿的，而且**不用在 `plugin.xml` 里加任何 `<depends>`**。三条硬约束：

- `StructureViewModel extends Disposable`，**必须在 `finally` 里 `dispose()`**，否则漏掉它内部挂的监听。
- 不是 `TreeBasedStructureViewBuilder` 的 builder（图片、二进制之类的自定义编辑器）拿不到节点，直接给一句「这类文件没有结构信息」，没有别的办法。
- **不能用 `LanguageStructureViewBuilder`**：`INSTANCE` 字段在 2026.2 已废弃，`getInstance()` 在 2022.3 还不存在，两头都不占。走 `FileEditor` 这条路两个版本都干净。

**判断一个节点是方法还是属性，不认任何具体语言的 PSI 类**（认了就得加 `<depends>`，而且每多支持一种语言就得改一次）：往上翻实现类的父类链、每一层再翻它的接口，只比**简单名**——含 `Method` / `Function` / `Constructor` 算方法，含 `Field` / `Property` / `Variable` / `Constant` 算属性，兜底看显示文字里有没有 `(`。两头都不沾的归 `KIND_OTHER`，而 **OTHER 永远显示**：宁可多显示几行，也不要因为认不出类别就把整个类连着它的方法一起藏掉。另外**过滤只作用在叶子上**（`children.isEmpty()`），内层节点（类、接口）被滤掉的话它下面的成员就成了孤儿。

拉杆的层数**同时是构建深度和展开深度**：`build()` 到了 `maxDepth` 就不再递归，建完整棵树全展开。上限 10 层，再深在这个宽度的侧边栏里已经没法看。节点数另有 `MAX_NODES = 3000` 的上限，到了就停下并在末尾补一行灰字提示——几千行的压缩 js / 生成代码结构树能有上万个节点，全建出来再全展开会把 EDT 卡住。

刷新只在**切编辑器**（`FileEditorManagerListener.FILE_EDITOR_MANAGER` 的 `selectionChanged`）和**点刷新按钮**时发生，没挂 PSI 变化监听：边打字边重建整棵树太贵。`connect(project)` 把连接挂在项目上，项目关掉自动断开，不用自己 dispose。

## 读注释（`PsiCommentUtils`）

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

