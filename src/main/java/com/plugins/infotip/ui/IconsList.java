package com.plugins.infotip.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

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
    private Icons selectedItem;

    public IconsList() {
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(okButton);
        okButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onOK();
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

    private void onOK() {
        selectedItem = (Icons) comboBox1.getSelectedItem();
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
