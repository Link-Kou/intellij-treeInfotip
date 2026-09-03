# [![TreeInfotip](trees2.svg)](https://github.com/yc-2018/intellij-treeInfotip) TreeInfotip

![GitHub release (latest by date)](https://img.shields.io/github/v/release/yc-2018/intellij-treeInfotip)
![GitHub](https://img.shields.io/github/license/yc-2018/intellij-treeInfotip)
![GitHub issues](https://img.shields.io/github/issues/yc-2018/intellij-treeInfotip)
![JetBrains plugins](https://img.shields.io/jetbrains/plugin/d/12994)
![JetBrains Plugins](https://img.shields.io/jetbrains/plugin/v/12994)

### TreeInfotip 能做什么？

> 给 IntelliJ 系 IDE 的项目目录树加备注。在目录树里右键选中文件或目录，就能挂上说明文字、改颜色、换图标，配置全部落在项目根目录的一个 XML 里，跟着项目走。

### 功能

> 文字备注：在节点名后面缀一段说明

> 覆盖显示名称：用自定义名称替换节点显示的文件名，留空恢复原名

> 悬浮提示：自定义鼠标停在节点上时弹出的内容

> 颜色与图标：设置文字色、背景色，或换成 IDE 内置的任意一个图标

> 删除线：给废弃的文件或目录名划一道横线

> 按扩展名批量设置：一条规则命中一批同扩展名的文件，范围可限定为当前目录（含子目录）或整个项目

> 侧边栏导航窗口：把配置过的节点集中列出来，双击就跳到对应文件

> XML 编辑工具窗口：直接查看和编辑配置文件，不用去项目根目录翻

### 配置存在哪

> 项目根目录的 `DirectoryV3.xml`，不写进 IDE 的全局设置。想同步给同事就把它一起提交，想丢掉全部配置就删了它。手动改完保存即时生效。

### 为什么要这个插件

> 1、用来对付一些火葬场项目用的。第一目录命名的问题，见名知其意，难度不小。英文啊！都是泥腿子。翻译器翻译的，人都闷逼了。只有求理解万岁了。

> 2、方便小白同学，我看到过些同学，入手项目看到目录就一个头大。好记忆不如烂笔头，充分发挥了知识分子的优良传统。手动写本本。

### 使用环境

`IntelliJ 平台 2022.3 及以上`

只依赖 `com.intellij.modules.lang`，所以 IDEA / WebStorm / PyCharm / GoLand / PhpStorm / RubyMine / CLion / Rider 这些 JetBrains 家的 IDE 都能装，社区版同样可用。

用 2022.2 及更早版本的请停留在 `5.0.4`，从 `5.1.0` 起字节码是 Java 17，老 IDE 装不上。

### 在线安装(搜索)

IDE -> <kbd>Preferences</kbd> -> <kbd>Plugins</kbd> -> <kbd>TreeInfotip</kbd>

![样例](https://raw.githubusercontent.com/yc-2018/intellij-treeInfotip/master/image/2023-04-14_14.54.35.png "样例")

### 源代码构建

    项目管理：Gradle，需要 JDK 17

    仓库里没有提交 gradle-wrapper.jar，所以 ./gradlew 用不了，请直接用本机 Gradle 跑 buildPlugin

    注意：国内网络原因，第一次拉平台依赖十分费力，耐心一点

### 示例

> ##### 图片示例：

![样例](https://raw.githubusercontent.com/yc-2018/intellij-treeInfotip/master/image/2023-04-14_14.51.58.png "样例")
![样例](https://raw.githubusercontent.com/yc-2018/intellij-treeInfotip/master/image/2023-04-14_14.52.35.png "样例")
