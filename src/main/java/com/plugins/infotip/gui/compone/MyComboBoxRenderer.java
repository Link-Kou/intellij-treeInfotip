package com.plugins.infotip.gui.compone;

import com.plugins.infotip.gui.IconsUtils;
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
        //和树里保持一致:下拉框预览也缩到 16,免得选的时候看着一个大小、设上去又是另一个大小
        setIcon(IconsUtils.fit(value.getIcon()));
        setText(value.getName());
        return this;
    }
}
