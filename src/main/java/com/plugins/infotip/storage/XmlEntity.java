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
     * XMl
     */
    private XmlTag tag;

}
