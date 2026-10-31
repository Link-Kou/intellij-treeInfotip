# V7 嵌套结构与老配置迁移（7.0.0 起）

> 本文从 CLAUDE.md 抽离。**改 `LegacyReader` / `NodePaths` / `NodeWriter` / `V7Migrator`、或碰嵌套读写与迁移逻辑时必读。**

## 为什么改嵌套

6.x 是平铺的，一条 `<tree path="/很长的/目录/A.java">` 就得把长目录完整写一遍，所以 6.0.0 专门做了「抽离路径前缀」把重复目录提到 `<prefixes>` 表里。V7 直接把配置做成**嵌套树**：一个 `<node>` 套下一层 `<node>`，每层 `path` 只写相对上一层那截，重复的长目录天生只写一遍。整套前缀机制（`PathPrefixes` / `PathPrefixEditor` / `PrefixIssue` / `XmlPrefixDialog` 四个类和「抽离或还原路径前缀」按钮）因此一并删掉了。

同时属性名全部改短，对得上字段名：`title→note`、`presentableText→label`、`tooltipTitle→tooltip`、`textColor→color`、`backgroundColor→bg`、`strikethrough→strike`。7.1.0 又去掉了 `extension` 属性和「按扩展名批量设置」，一条规则只对应一个完整路径；迁移时带 `extension` 的老「类型规则」直接丢弃（`LegacyReader.tree` 跳过它们）。

```xml
<trees>
    <node path="src/main/java" note="源码">
        <node path="Foo.java" note="入口"/>       <!-- 完整路径 /src/main/java/Foo.java -->
        <node path="App.java" color="255,0,0"/>
    </node>
</trees>
```

## 一条贯穿始终的不变量：内存里存完整路径

`XmlStorage.parsing` 先序递归，一路把每层 `path` 累加（`NodePaths.join`）成完整路径存进 `XmlEntity.path`。所以**匹配、右键菜单、侧边栏那些地方完全不知道嵌套这回事**，`TreesUtils.getMatchPath` 和 6.x 一个字没改。这和 6.x 时代「内存里存展开后的完整路径、prefix 只在读写两头处理」是同一个思路。

## 代码分工

- **`NodePaths`**：纯字符串算法，路径的 `join` / `relativize` / `segments` / 归一。无平台依赖，可照 CLAUDE.md 说的抄成单文件 Java 跑断言。
- **`V7Migrator`**：把一串完整路径的规则建成树、压平单链、输出整个 V7 文件正文。也是纯字符串。
- **`LegacyReader`**：读 6.x / 5.x 的平铺文件（含展开 `prefix`、映射老属性名），只在迁移时用一次。
- **`NodeWriter`**：在活的 PSI 树上定位 / 新建 / 拆分 / 清理 `<node>`，`XmlStorage` 的写操作调它。
- **`XmlFileUtils.migrateIfNeeded`**：迁移入口，在 `PluginStartupActivity` 里第一步调。

## 几个反直觉的点，改之前先看清楚

- **`NodePaths.relativize` 必须卡在 `/` 上**，不能用裸的 `startsWith`。`/a/CarrierRecruit` 和 `/a/CarrierRecruitReg` 是字符串前缀关系，裸比会把后者算成前者底下的 `Reg`，路径当场错掉。返回值三态：剩下那截 / 空串（正好等于父路径）/ `null`（不在父路径下）。这个坑 6.x 的 `stripPrefix` 踩过一次。
- **重排之后命中结果不变**，靠的是「只有完整路径相同的规则才互相竞争」：规则要完整路径全等才命中，而路径相同的规则归堆后都落在同一个父节点里，`V7Migrator` 保留了同一节点内的相对顺序。跨路径的规则本来就不靠顺序分胜负。
- **`V7Migrator` 压平单链**：`/src/main/java` 底下才有规则时写成一个 `<node path="src/main/java">` 而不是三层空壳。判据是「自己没有规则、且只有一个孩子」，有规则的节点绝不能被并掉——规则挂在这一层的路径上。
- **`NodeWriter.ensurePath` 要能拆开压平过的节点**：定位到 `main` 这一层时，如果现有的是 `<node path="main/java">`，得先 `split` 成两层。拆分时**属性和子节点全跟着里层走**，它们本来就属于那个更深的路径。
- **纯容器节点删空要跟着消失**：`NodeWriter.deleteRule` 删掉规则后，`prune` 自下而上把没内容（没子节点、没设置属性）的容器一路清到还有内容的那层，否则文件里会攒一堆空壳。
- **属性值里的换行写成 `&#10;`**：XML 规范要求解析器把属性值里的裸换行归一成空格，悬浮提示是多行的，直接写裸换行再读出来就少了换行。`V7Migrator.escape` 负责这件事。

## 迁移策略：只转一次，老文件原样留着

`migrateIfNeeded` 的判据就一条：**已经有 `DirectoryV7.xml` 就什么都不做**。没有才去找 V6（优先）/ V3，找到就按它的内容生成一份 V7 并发通知。

- **老文件不改名也不删**。它在用户版本库里，别的同事可能还在用旧版插件，删了对方就全丢了；留着的代价只是多一个文件。这和 6.x「改名让新旧各读各的」是不同取舍——那时是同一份文件改名，现在是各自独立的两份。
- 因此迁移只发生一次：V7 一旦生成，下次开项目直接命中判据回来。用户要重新迁移，把 V7 删掉再开一次即可。
- `migrateIfNeeded` 要开写操作（落盘），别在写操作或 PSI 事件回调里调。读老文件的 PSI 属性要包读操作。
- 运行时**只认 V7**（`XmlFileUtils.isFileName` 只认 V7 一个名字、`findConfigFile` 只找 V7）：老文件读完就退休，之后再改它不触发重新解析，否则会把内存里的 V7 配置覆盖成老内容、而下次存盘又写回 V7，两份从此对不上。
