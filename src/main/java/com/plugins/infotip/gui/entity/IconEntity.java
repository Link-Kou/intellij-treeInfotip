package com.plugins.infotip.gui.entity;

import javax.swing.*;

/**
 * A <code>Icons</code> Class
 *
 * @author lk
 * @version 1.0
 * @date 2021/6/18 19:59
 */
public class IconEntity {
    Icon icon;
    String name;

    public Icon getIcon() {
        return icon;
    }

    public IconEntity setIcon(Icon icon) {
        this.icon = icon;
        return this;
    }

    public String getName() {
        return name;
    }

    public IconEntity setName(String name) {
        this.name = name;
        return this;
    }
}
