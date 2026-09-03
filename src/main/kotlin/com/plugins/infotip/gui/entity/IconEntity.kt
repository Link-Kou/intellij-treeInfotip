package com.plugins.infotip.gui.entity

import javax.swing.Icon

/**
 * 图标下拉框的一项：图标本体 + 用于写进 XML 的名字（如 `Actions.Edit`）。
 *
 * 原来是手写的链式 setter 版本（33 行），Kotlin 的 data class 直接生成
 * `getIcon()` / `getName()` 供 Java 调用，equals/hashCode/toString 也一并有了。
 * 构造点只有 [com.plugins.infotip.gui.IconsUtils] 一处，所以去掉 setter 不影响别人。
 *
 * @author lk
 */
data class IconEntity(
    val icon: Icon,
    val name: String,
)
