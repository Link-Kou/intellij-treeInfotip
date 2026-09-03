package com.plugins.infotip.storage;

import com.intellij.psi.xml.XmlTag;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * XML实体对象
 */
@Data
@Accessors(chain = true)
public class XmlEntity {
    /**
     * 路径
     */
    private String path;

    /**
     * 后缀
     */
    private String extension;

    /**
     * 标题
     */
    private String title;

    /**
     * 覆盖文本
     */
    private String presentableText;

    /**
     * 提示文本
     */
    private String tooltipTitle;

    /**
     * 图标
     */
    private String icon;

    /**
     * 文本颜色
     */
    private String textColor;

    /**
     * 背景色
     */
    private String backgroundColor;

    /**
     * 删除线，取值 "true" 表示开启；null 或其他值表示关闭
     */
    private String strikethrough;

    /**
     * XMl
     */
    private XmlTag tag;

    /**
     * 是否开启了删除线
     *
     * @return true 表示需要给节点文本加删除线
     */
    public boolean isStrikethroughEnabled() {
        return "true".equalsIgnoreCase(strikethrough);
    }

}
