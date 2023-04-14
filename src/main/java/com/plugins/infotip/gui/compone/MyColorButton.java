package com.plugins.infotip.gui.compone;

import com.intellij.openapi.util.SystemInfo;
import com.plugins.infotip.gui.IconsUtils;

import javax.swing.*;
import java.awt.*;

public class MyColorButton extends JButton {

    private Color myColor = Color.WHITE;

    public MyColorButton() {
        setMargin(new Insets(0, 0, 0, 0));
        setFocusable(false);
        setDefaultCapable(false);
        setFocusable(false);
        setColor(myColor);
        setSize(new Dimension(45, 45));
        setPreferredSize(new Dimension(45, 45));
        setPressedIcon(IconsUtils.MyBatisIcon);
        if (SystemInfo.isMac) {
            putClientProperty("JButton.buttonType", "square");
        }
    }

    public MyColorButton(Color myColor) {
        super();
        this.myColor = myColor;
    }


    @Override
    public void paint(Graphics g) {
        int width = getWidth();
        int height = getHeight();
        if (myColor != null) {
            g.setColor(myColor);
            g.fillRect(0, 0, width, height);
        } else {
            g.setColor(Color.WHITE);
            g.drawRect(0, 0, width, height);
        }
    }

    public void setColor(Color myColor) {
        this.myColor = myColor;
    }

    public Color getColor() {
        return this.myColor;
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(12, 12);
    }
}