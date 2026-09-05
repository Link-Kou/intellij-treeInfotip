package com.plugins.infotip.gui

import com.intellij.openapi.util.IconLoader
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.IconUtil
import com.plugins.infotip.gui.entity.IconEntity
import java.util.concurrent.ConcurrentHashMap
import javax.swing.Icon

/**
 * 反射 `com.intellij.icons.AllIcons`，把 IDE 内置的全部图标摊平成一个列表，
 * 供颜色/图标对话框的下拉框使用。图标名（如 `Actions.Edit`）会原样写进 XML。
 *
 * 注意这是全项目最脆弱的一块：JetBrains 删掉或改名任何一个图标字段，
 * 老配置里的名字就静默匹配不上（[findFitIcon] 查不到就不设图标）。
 *
 * 反射的部分来自 lk 的 Java 版，5.1.1 改写成 Kotlin；缩放（[fit]）是后来加的。
 *
 * @author lk
 * @author yc556&claude-opus-5
 */
object IconsUtils {

    private const val CLASS_NAME = "com.intellij.icons.AllIcons"

    /**
     * 目录树一行容得下的图标边长（逻辑像素，用时再乘 HiDPI 缩放）。
     *
     * AllIcons 不是清一色 16×16：2022.3 下反射得到的 1080 个字段里有 50 个长边超过 16——
     * 20×20 六个、24×24 十一个、32×32 二十个（`ProcessBigStep_*` 那批）、40×40 两个
     * （`PluginsPluginLogo`）、48×48 一个，还有 18×22、32×15、2×19 这类非正方形的。大图标直接塞进
     * `PresentationData`，树会为了容纳它把整行撑高，那一行看着比别人突出一截。
     */
    private const val TREE_ICON_SIZE = 16

    /** Java 侧按 `IconsUtils.MyBatisIcon` 访问，所以必须是 `@JvmField`（否则只有 getter） */
    @JvmField
    val MyBatisIcon: Icon = IconLoader.getIcon("/icons/mybatis.png", IconsUtils::class.java)

    //必须声明在下面 init 块之前：object 的属性和 init 按书写顺序执行
    private val ICONS = ArrayList<IconEntity>()

    /** 图标名 → 条目。没有它就得像以前那样，每个节点每次重绘都线性扫上千条再逐个比字符串 */
    private val BY_NAME = HashMap<String, IconEntity>()

    /** [fit] 的结果缓存。树重绘很频繁，不缓存等于每帧都新建一个缩放图标 */
    private val FITTED = ConcurrentHashMap<Icon, Icon>()

    /** [FITTED] 那批结果是按哪个目标边长算出来的；DPI 或界面字号一变就整批作废 */
    @Volatile
    private var fittedFor = -1

    init {
        //用 runCatching 而不是 catch (Exception)：它连 Throwable 一起兜住。
        //静态初始化里漏出去的异常会变成 ExceptionInInitializerError，
        //而 AllIcons 在新版 IDE 上整个换掉时抛的正是 NoClassDefFoundError 这类 Error，
        //兜不住就是插件直接加载失败。兜住了最坏也只是"没有图标可选"。
        runCatching {
            for (clazz in flattenNested(Class.forName(CLASS_NAME).classes)) {
                //类名去掉 AllIcons 前缀再去掉 $，得到 "Actions." / "General." 这样的前缀
                val prefix = clazz.name.removePrefix(CLASS_NAME).replace("$", "")
                for (field in clazz.fields) {
                    if (field.type != Icon::class.java) continue
                    val icon = field.get(null) as? Icon ?: continue
                    val entity = IconEntity(icon, prefix + field.name)
                    ICONS += entity
                    //同名字段撞了就后者覆盖前者。原来那个线性扫描没有 break，
                    //等于最后一个匹配项生效，这里保持一致。
                    BY_NAME[entity.name] = entity
                }
            }
        }.onFailure { it.printStackTrace() }
    }

    /** 返回的是内部列表本身（沿用原行为，调用方只读） */
    @JvmStatic
    fun getAllIcons(): ArrayList<IconEntity> = ICONS

    /**
     * 按 XML 里存的名字取图标，并保证尺寸不超过一行的高度；查不到返回 null。
     */
    @JvmStatic
    fun findFitIcon(name: String?): Icon? {
        val entity = BY_NAME[name ?: return null] ?: return null
        return fit(entity.icon)
    }

    /**
     * 只缩不放：长边超过 [TREE_ICON_SIZE] 的等比缩到刚好塞进去，本来就不大的原样返回。
     *
     * 不放大是刻意的——小图标（比如 13×13 的 Toolwindows 那批）拉大只会变糊，
     * 而且会平白改掉已有配置的观感。
     *
     * 只在真正要用某个图标时才调用，不在 [init] 里对全部图标预先算一遍：
     * `getIconWidth()` 会迫使 `CachedImageIcon` 立刻把 SVG 光栅化，
     * 上千个一起来就是一次可感知的卡顿。
     */
    @JvmStatic
    fun fit(icon: Icon): Icon {
        val max = JBUIScale.scale(TREE_ICON_SIZE)
        if (max != fittedFor) {
            //DPI / 界面字号变了，之前按老尺寸缩的那批不能再用
            FITTED.clear()
            fittedFor = max
        }
        //getIconWidth 返回的已经乘过 HiDPI 缩放，和 JBUIScale.scale 是同一个量纲
        val longest = maxOf(icon.iconWidth, icon.iconHeight)
        if (longest <= 0 || longest <= max) return icon
        //传给 IconUtil.scale 的是 OBJ_SCALE（相对倍率），平台会把它叠在 DPI 缩放之上，
        //比自己算一个绝对像素尺寸安全。
        return FITTED.computeIfAbsent(icon) { IconUtil.scale(it, null, max.toFloat() / longest) }
    }

    /**
     * 递归摊平 AllIcons 下的嵌套类（Actions / General / Nodes …，还有更深一层的）。
     * 先递归再放自己，和原 Java 版的后序顺序一致，下拉框里的排列不变。
     */
    private fun flattenNested(classes: Array<Class<*>>): List<Class<*>> =
        classes.flatMap { flattenNested(it.classes) + it }
}
