
# TreeInfotip

### 2.0时代来袭,更加具备人性化。

> 通过目录树右键添加备注
> 重构代码代码结构更加合理

### TreeInfotip能做什么？

> TreeInfotip是基于IntelliJ开发的项目目录自定义备注显示，主要通过自定义XML来生成项目目录树备注。

### 为什么要这个插件

> 1、用来对付一些火葬场项目用的。第一目录命名的问题，见名知其意，难度不小。英文啊！都是泥腿子。翻译器翻译的，人都闷逼了。只有求理解万岁了。

> 2、方便小白同学，我看到过些同学，入手项目看到目录就一个头大。好记忆不如烂笔头，充分发挥了知识分子的优良传统。手动写本本。

### 使用环境

`IntelliJ IDEA Ultimate版（172+）`

`WebStrom（172+）`

### 源代码构建

    项目管理：Gradle
    
    注意：国内网络原因，构建十分费力，耐心一点

### 在线安装(搜索)

IDEA或WebStrom -> Preferences -> Plugins -> TreeInfotip

### 手动安装

[plugin.intellij.assistant-2.1.0.jar](https://raw.githubusercontent.com/Link-Kou/intellij-treeInfotip/master/plugin/plugin.intellij.assistant-2.1.0.jar)

### 一、示列

> ##### 图片示列教程：（国内有些网络啊！tmd图片看不了的）


![样列](https://raw.githubusercontent.com/Link-Kou/intellij-treeInfotip/master/image/2020-03-18_16-46-20.gif "样列")
![样列](https://raw.githubusercontent.com/Link-Kou/intellij-treeInfotip/master/image/2020-03-18_16-47-30.jpg "样列")
![样列](https://raw.githubusercontent.com/Link-Kou/intellij-treeInfotip/master/image/2021-06-08_16_40_11.png "样列")


> ##### 说明文档：

1. 1.0和2.0不兼容
   
2. 在项目根目录下创建DirectoryV2.xml文件(文件名称不能改变)

3. 文件内容示列

```xml：
  <?xml version="1.0" encoding="UTF-8"?>
  <trees>
      <tree path="/src"/>
      <tree path="/builds" title="构建文件"/>
      <tree path="/image" title="图片示例"/>
  </trees>
```

3. 标签说明

```xml：

  //trees只能有一个，所有子标签都在此标签里面
  <trees/>

  //文件夹或文件说明
  //path 路径，MAC与Win的路径都是通用的，上下级的标签会拼接上父节点的path属性 
  //title 显示的内容
  //extension 后缀
  
  <tree path="" title=""  extension=""/> 
```