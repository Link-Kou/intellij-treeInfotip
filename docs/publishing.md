# 插件身份、Marketplace 与 Plugin Verifier

> 本文从 CLAUDE.md 抽离。**改 `plugin.xml` 的 id/name/description、或准备上传 Marketplace 时必读。**

## 插件身份与全局 id（5.2.0 起）

本仓库是 `Link-Kou/intellij-treeInfotip` 的复刻。JetBrains 不会在原作者不配合的情况下把已有的 Marketplace 条目转给新 vendor，所以复刻版只能作为**另一个插件**发布，于是要和原版划清四套互不相干的命名空间：

| 名字 | 在哪 | 撞了会怎样 |
|---|---|---|
| `<id>` = `com.github.yc556.treeinfotip` | `plugin.xml` | IDE 的更新检查按 id 去 Marketplace 查，沿用原 id 会被原版的构建静默"更新"掉 |
| `<name>` = `TreeInfoTip Notes` | `plugin.xml` | Marketplace 条目名要唯一。源码用纯拉丁名，release 工作流注入 `TreeInfoTip Notes / 目录树备注`；各上传通道的校验差异见下面一节 |
| `intellij.pluginName` = `TreeInfoTip-Notes` | `build.gradle` | 它是 zip 根目录名，也就是装完后 `plugins/<这个名字>/`；和原版同名时后装的直接覆盖前一个的安装目录 |
| action / group / toolWindow / notificationGroup 的 id | `plugin.xml` | 这些注册表是 IDE 全局的，重名会被拒绝注册。action / group 使用 `TreeInfotip` 前缀，toolWindow / notificationGroup 使用 `TreeInfoTip Notes` 名称 |

`group 'com.github.yc556'`（`build.gradle`）只是 Gradle 坐标，纯装饰，和上面四个都无关。

**工具窗口 id 直接作为侧边栏标题**：现在只有一个窗口，id 是 `TreeInfoTip Notes`，通知组 id 同名，无需额外的文案资源文件（6.0.0 去掉了底部那个 `TreeInfoTip Notes XML` 窗口）。改名直接更新这些 id，不迁移旧 id 保存的窗口布局或通知设置；通知代码中的 `NOTIFICATION_GROUP` 必须与 `plugin.xml` 一致——`XmlFileUtils` 和 `OldPluginConflictNotifier` 里各有一份常量。

action id 不对用户显示（菜单文字来自 `text=` 属性），改名只会丢掉用户自己配的快捷键——这些 action 本来就没有默认快捷键，可以忽略。

`OldPluginConflictNotifier` 在 `PluginStartupActivity.runActivity` 末尾检测旧 id `com.linkkou.plugin.intellij.assistant` 还在不在，一个 IDE 会话只弹一次通知。这一条同时覆盖两种人：装着原版的，和从本插件 5.1.x 升上来的（那些构建用的就是这个旧 id）。

两个插件同时装着时的实际情况，别搞反：

- **7.0.0 起两边连配置文件都不共用**：本插件读 `DirectoryV7.xml`（嵌套结构、属性名也换了），旧版只认 `DirectoryV3.xml`。所以旧版看不到新加的备注，反过来也一样。开项目时若只有旧文件，本插件会按它生成一份 V7（**旧文件原样保留**），之后各读各的，详见 `docs/v7-migration.md`。
- 真正的代价是每次重绘算两遍，而且两个装饰入口的执行顺序不定，**旧版跑在后面时会 `clearText()` 掉新版才有的覆盖显示名称等设置**。
- 检测到冲突时**不要**顺手跳过自己的装饰：跳过等于把渲染完全交给旧版，用户必然看不到新特性；两边都跑最坏也就是退化成旧版的效果，是弱优于跳过的。
- 也没有用 `<incompatible-with>`（2022.3 确实支持，`XmlReader` 解析进 `RawPluginDescriptor.incompatibilities`，`PluginSetBuilder` 执行）。它直接让插件不加载，太硬；而且它和 `com.intellij.pluginReplacement` 互斥——插件都不加载了，自然也注册不了那个 EP。

## Marketplace 的描述符校验（5.3.1 起）

上传 zip 时 Marketplace 会校验 `plugin.xml`，不过这一关就传不上去。踩过的两条：

- **`<name>` 只能用拉丁字符**。放行的是字母、数字、空格和 `.,+_-/:()#'&[]|`，中日韩文字直接判"包含无效字符"。5.2.0 设的 `TreeInfotip 目录树备注` 就是这么被网页上传拒掉的（5.3.1 改成 `TreeInfotip Notes`）。Plugin Verifier 1.393 的发布说明把这条写死成 "Plugin name must be in Latin characters"。
- **`<description>` 要以拉丁字符开头、正文至少 40 字**。正文里的中文没问题，现在开头那句 `TreeInfoTip Notes plugin for IntelliJ IDEs.` 正好满足，**改描述时别把中文段落挪到最前面**，emoji 放开头也会被拒。

### 中文名到底行不行：分上传通道，不分首次/更新（5.4.0 实测）

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

## Plugin Verifier 的 API 校验（5.3.2 起）

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

### 选替代 API 的方法：两份 classpath 一起比

难点在于 `since-build=223` 要求替代品在 **2022.3.2 就存在**，而它同时得在 **verifier 的目标版本上不是 internal / 不带删除标记**。只看一头必然踩坑（我第一版把 6 参 `showDialog` 选进去，就是只扫了 2026.2 没扫 2022.3）。两份 lib 目录：

- 2022.3.2（编译基线）：`/d/green/Gradle/repository/caches/modules-2/files-2.1/com.jetbrains.intellij.idea/ideaIU/2022.3.2/<hash>/ideaIU-2022.3.2/lib` —— 注意有一层 hash 目录，`ls -d .../ideaIU/*/` 找不到，用 `find ... -name app.jar` 定位。
- 2026.2.1（目标）：`/d/app/JetBrains/IntelliJ IDEA 2026.2.1/lib`

用 `javap -v -cp "$(ls *.jar | tr '\n' ';')" <全限定类名>` 把方法声明和紧跟其后的 `RuntimeInvisibleAnnotations` 配对着看，就能判定每个重载的状态。已经查清的几条，省得再查一遍：

- `PluginManager.getInstance()` 和 `findEnabledPlugin()` 在 2026.2 都是 internal，**不能**当 `PluginManagerCore.getPlugin` 的替代品；`isPluginInstalled` / `isDisabled` 两个版本都干净。
- `PluginManagerConfigurable.getId()` 在两个版本都返回 `"preferences.pluginManager"`（`javap -c` 里能直接看到 `ldc // String preferences.pluginManager`），所以按 id 找 configurable 是稳的。**别改成 `ActionManager` 执行内置 action**：那个 action 的 id 2022.3 是 `WelcomeScreen.Plugins`、2026.2 已经改成 `ShowPlugins`，跨版本对不上。
- **`ColorChooserService.showDialog` 的两个重载废弃状态是反的**：不带 `Project` 的 6 参版在 **2022.3 就是 `@Deprecated(forRemoval)`**、到 2026.2 反而干净；带 `Project` 的 7 参版两个版本都干净。所以要用 7 参那个（`SelectColorIconsView` 为此加了 `Project` 构造参数）。`project` 形参可以传 `null`——2022.3 的形参没标 `@NotNull`，2026.2 的 Kotlin 实现也只对 `parent` 和 `listeners` 做 `checkNotNullParameter`。

