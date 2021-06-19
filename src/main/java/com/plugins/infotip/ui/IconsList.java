package com.plugins.infotip.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Objects;

import static com.plugins.infotip.FileIcons.getAllIcons;

/**
 * A <code>iconsList</code> Class
 *
 * @author lk
 * @version 1.0
 * @date 2021/6/18 13:11
 */
public class IconsList extends JDialog {
    private JPanel contentPane;
    private JComboBox<Icons> comboBox1;
    private JButton okButton;
    private JButton restoreButton;
    private Icons selectedItem;

    public IconsList() {
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(okButton);
        okButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onOk();
            }
        });
        restoreButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onRestoreButton();
            }
        });
        ComboBoxRenderer renderer = new ComboBoxRenderer();
        renderer.setPreferredSize(new Dimension(35, 25));
        comboBox1.setRenderer(renderer);
        ArrayList<Icons> allIcons = getAllIcons();
        for (Icons allIcon : allIcons) {
            comboBox1.addItem(allIcon);
        }
    }

    public void setIcons(String name) {
        ArrayList<Icons> allIcons = getAllIcons();
        for (Icons allIcon : allIcons) {
            String name1 = allIcon.getName();
            if (null != name1) {
                if (name1.equals(name)) {
                    comboBox1.setSelectedItem(allIcon);
                }
            }
        }
    }

    private void onOk() {
        selectedItem = (Icons) comboBox1.getSelectedItem();
        dispose();
    }

    private void onRestoreButton() {
        selectedItem = null;
        dispose();
    }

    public Icons getIcons() {
        return selectedItem;
    }

    static class ComboBoxRenderer extends JLabel implements ListCellRenderer<Icons> {

        @Override
        public Component getListCellRendererComponent(JList list, Icons value, int index, boolean isSelected, boolean cellHasFocus) {
            setIcon(value.getIcon());
            setText(value.getName());
            return this;
        }
    }
}
