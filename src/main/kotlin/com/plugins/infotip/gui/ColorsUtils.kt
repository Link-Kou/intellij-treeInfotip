package com.plugins.infotip.gui

import java.awt.Color

/**
 * 颜色与 `"r,g,b"` 字符串的互转。
 *
 * XML 里颜色统一存成 `"255,128,0"` 这种十进制三元组，这里是唯一的转换入口。
 *
 * 逻辑来自 lk 的 Java 版，5.1.1 改写成 Kotlin。
 *
 * @author lk
 * @author yc556&claude-opus-5
 */
object ColorsUtils {

    /**
     * 必须是 `const val`：[com.plugins.infotip.gui.view.SelectColorIconsView]
     * 在 Java 的 `switch` case 标签里用了它，而 case 标签只接受编译期常量。
     * 写成 `@JvmField val` 会编译不过。
     */
    const val COLOR_TEXT_COLOR_NAME = "TEXTCOLOR"

    const val COLOR_BACKGROUND_COLOR = "BACKGROUNDCOLOR"

    /** 颜色转 `"r,g,b"`；传 null 返回 null，保持和原 Java 版一致 */
    @JvmStatic
    fun toRBGStr(color: Color?): String? =
        color?.let { "${it.red},${it.green},${it.blue}" }

    /** `"r,g,b"` 转颜色；格式不对（分量不是 3 个）或传 null 都返回 null */
    @JvmStatic
    fun toColor(rgb: String?): Color? {
        val parts = rgb?.split(",") ?: return null
        if (parts.size != 3) return null
        //整数解析失败时返回 null，而不是像原来那样抛 NumberFormatException
        val (r, g, b) = parts.map { it.trim().toIntOrNull() ?: return null }
        return Color(r, g, b)
    }
}
