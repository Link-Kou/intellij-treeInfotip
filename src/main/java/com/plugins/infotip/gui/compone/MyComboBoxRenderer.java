package com.plugins.infotip.gui.compone;

import com.plugins.infotip.gui.entity.IconEntity;

import javax.swing.*;
import java.awt.*;

/**
 * A <code>MyComboBoxRenderer</code> Class
 *
 * @author lk
 * @version 1.0
 * <p><b>date: 2023/4/13 16:27</b></p>
 */
public class MyComboBoxRenderer extends JLabel implements ListCellRenderer<IconEntity> {

    @Override
    public Component getListCellRendererComponent(JList list, IconEntity value, int index, boolean isSelected, boolean cellHasFocus) {
        setIcon(value.getIcon());
        setText(value.getName());
        return this;
    }
}
