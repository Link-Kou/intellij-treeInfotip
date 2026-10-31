package com.plugins.infotip.storage;

import com.intellij.psi.xml.XmlTag;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 一条规则在内存里的样子
 *
 * <p>
 * 7.0.0 起配置文件是嵌套的（{@code <node>} 套 {@code <node>}），但<b>这里的 {@code path} 一律是
 * 展开后的完整路径</b>，以 {@code /} 开头、相对项目根目录。匹配、右键菜单、侧边栏那些地方
 * 因此完全不知道嵌套这回事，也就不用跟着改——和 6.x 时代 {@code prefix} 的处理方式是同一个思路。
 * </p>
 * <p>
 * 属性名 7.0.0 全部改短了（{@code title} → {@code note}、{@code presentableText} → {@code label} 等），
 * 字段名跟着 XML 属性名走，两边对得上。老文件由 {@link LegacyReader} 读成同样的实体，
 * 所以改名只影响读写两头，中间那一大片逻辑一个字没动。
 * </p>
 */
@Data
@Accessors(chain = true)
public class XmlEntity {
    /**
     * 完整路径，以 / 开头。写回文件时由 {@link XmlStorage} 按嵌套层级拆成每层的那一截
     */
    private String path;

    /**
     * 备注文字，灰色跟在节点名后面（6.x 叫 title）
     */
    private String note;

    /**
     * 覆盖节点显示的名字（6.x 叫 presentableText）
     */
    private String label;

    /**
     * 鼠标悬浮时的提示（6.x 叫 tooltipTitle）
     */
    private String tooltip;

    /**
     * 图标，AllIcons 里的字段路径
     */
    private String icon;

    /**
     * 文字颜色，十进制 r,g,b（6.x 叫 textColor）
     */
    private String color;

    /**
     * 背景色，写法同 {@link #color}（6.x 叫 backgroundColor）
     */
    private String bg;

    /**
     * 删除线，取值 "true" 表示开启；null 或其他值表示关闭（6.x 叫 strikethrough）
     */
    private String strike;

    /**
     * 对应的 {@code <node>} 标签，删除和置顶直接拿它操作
     */
    private XmlTag tag;

    /**
     * 是否开启了删除线
     *
     * @return true 表示需要给节点文本加删除线
     */
    public boolean isStrikeEnabled() {
        return "true".equalsIgnoreCase(strike);
    }

    /**
     * 这条规则有没有配任何看得见的东西
     *
     * <p>
     * 嵌套之后文件里会出现<b>纯容器节点</b>：{@code <node path="src">} 自己什么都没配，只是为了
     * 把底下几条规则挂上去。它们不是规则，不能进内存列表——否则侧边栏的 {@code ruleKey} 查重会把
     * 容器和同路径的真规则判成重复，真规则被标灰说成「不生效」。
     * </p>
     *
     * @return 配了至少一个样式属性时为 true
     */
    public boolean hasAnySetting() {
        return isNotBlank(note) || isNotBlank(label) || isNotBlank(tooltip)
                || isNotBlank(icon) || isNotBlank(color) || isNotBlank(bg)
                || isStrikeEnabled();
    }

    private static boolean isNotBlank(String value) {
        return null != value && !value.trim().isEmpty();
    }

}
