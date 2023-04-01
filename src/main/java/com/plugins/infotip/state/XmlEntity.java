package com.plugins.infotip.state;

import com.intellij.psi.xml.XmlTag;

/**
 * XML的实体
 *
 * @author lk
 * @version 1.0
 * 2021/6/7 17:11
 */
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
     * XMl
     */
    private XmlTag tag;

    /**
     * 图标
     */
    private String icon;

    /**
     * 颜色
     */
    private String color;

    //region  getset

    public String getPath() {
        return path;
    }

    public XmlEntity setPath(String path) {
        this.path = path;
        return this;
    }

    public String getExtension() {
        return extension;
    }

    public XmlEntity setExtension(String extension) {
        this.extension = extension;
        return this;
    }

    public String getTitle() {
        return title;
    }

    public XmlEntity setTitle(String title) {
        this.title = title;
        return this;
    }

    public XmlTag getTag() {
        return tag;
    }

    public XmlEntity setTag(XmlTag tag) {
        this.tag = tag;
        return this;
    }

    public String getIcon() {
        return icon;
    }

    public XmlEntity setIcon(String icon) {
        this.icon = icon;
        return this;
    }

    public String getColor() {
        return color;
    }

    public XmlEntity setColor(String color) {
        this.color = color;
        return this;
    }

    //endregion
}
