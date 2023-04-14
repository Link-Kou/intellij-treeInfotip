package com.plugins.infotip.gui.view;

import com.intellij.ui.ColorChooser;
import com.plugins.infotip.gui.ColorsUtils;
import com.plugins.infotip.gui.IconsUtils;
import com.plugins.infotip.gui.compone.MyComboBoxRenderer;
import com.plugins.infotip.gui.entity.IconEntity;
import com.plugins.infotip.gui.compone.MyColorButton;
import org.javatuples.Pair;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class SelectColorIconsView extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JComboBox<IconEntity> comboBox_icon;
    private JPanel jpanel_text_color;
    private JPanel jpanel_background_color;

    private String iconsName;
    private Color backgroundColor;

    private Color textColor;

    Map<String, Pair<MyColorButton, Color>> mapColor = new HashMap<String, Pair<MyColorButton, Color>>() {{
        put(ColorsUtils.COLOR_TEXT_COLOR_NAME, null);
        put(ColorsUtils.COLOR_BACKGROUND_COLOR, null);
    }};

    public SelectColorIconsView() {
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(buttonOK);


        //region 自定义组件
        MyComboBoxRenderer myComboBoxRenderer = new MyComboBoxRenderer();
        comboBox_icon.setRenderer(myComboBoxRenderer);
        ArrayList<IconEntity> allIcons = IconsUtils.getAllIcons();
        for (IconEntity allIcon : allIcons) {
            comboBox_icon.addItem(allIcon);
        }

        final Iterator<Map.Entry<String, Pair<MyColorButton, Color>>> iterator = mapColor.entrySet().iterator();
        while (iterator.hasNext()) {
            final Map.Entry<String, Pair<MyColorButton, Color>> next = iterator.next();
            final Pair<MyColorButton, Color> value = next.getValue();
            if (null == value) {
                final MyColorButton myColorButton = new MyColorButton(null);
                myColorButton.addActionListener(e -> onColor(next.getKey(), myColorButton));
                switch (next.getKey()) {
                    case ColorsUtils.COLOR_TEXT_COLOR_NAME:
                        this.jpanel_text_color.add(myColorButton);
                        break;
                    case ColorsUtils.COLOR_BACKGROUND_COLOR:
                        this.jpanel_background_color.add(myColorButton);
                        break;
                    default:
                        break;
                }
                mapColor.put(next.getKey(), Pair.with(myColorButton, null));
            }
        }

        //endregion

        buttonOK.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onOK();
            }
        });
        buttonCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        });
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });
        contentPane.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    }


    private void onColor(String type, MyColorButton myColorButton) {
        Color newColor = ColorChooser.chooseColor(this, "Choose Color", Color.white, true);
        if (null != newColor) {
            myColorButton.setColor(newColor);
            this.mapColor.put(type, Pair.with(myColorButton, newColor));
        }
    }

    /**
     * 设置图标
     *
     * @param name 名称
     */
    public void setIcons(String name) {
        ArrayList<IconEntity> allIcons = IconsUtils.getAllIcons();
        for (IconEntity allIcon : allIcons) {
            String name1 = allIcon.getName();
            if (null != name1) {
                if (name1.equals(name)) {
                    this.iconsName = name;
                    comboBox_icon.setSelectedItem(allIcon);
                }
            }
        }
    }

    public String getIcons() {
        return this.iconsName;
    }

    public void setBackgroundColor(String colorname) {
        final Color color = ColorsUtils.toColor(colorname);
        if (null != color) {
            for (Map.Entry<String, Pair<MyColorButton, Color>> next : mapColor.entrySet()) {
                if (ColorsUtils.COLOR_BACKGROUND_COLOR.equals(next.getKey())) {
                    final Pair<MyColorButton, Color> value = next.getValue();
                    final MyColorButton value0 = value.getValue0();
                    value0.setColor(color);
                    this.backgroundColor = color;
                    mapColor.put(next.getKey(), Pair.with(value0, color));
                }
            }
        }

    }

    public String getBackgroundColor() {
        return ColorsUtils.toRBGStr(this.backgroundColor);
    }

    public void setTextColor(String colorname) {
        final Color color = ColorsUtils.toColor(colorname);
        if (null != color) {
            for (Map.Entry<String, Pair<MyColorButton, Color>> next : mapColor.entrySet()) {
                if (ColorsUtils.COLOR_TEXT_COLOR_NAME.equals(next.getKey())) {
                    final Pair<MyColorButton, Color> value = next.getValue();
                    final MyColorButton value0 = value.getValue0();
                    value0.setColor(color);
                    this.textColor = color;
                    mapColor.put(next.getKey(), Pair.with(value0, color));
                }
            }
        }
    }

    public String getTextColor() {
        return ColorsUtils.toRBGStr(this.textColor);
    }

    private void onOK() {
        // add your code here
        IconEntity selectedItem = (IconEntity) comboBox_icon.getSelectedItem();
        if (null != selectedItem) {
            this.iconsName = selectedItem.getName();
        }
        for (Map.Entry<String, Pair<MyColorButton, Color>> next : mapColor.entrySet()) {
            final Pair<MyColorButton, Color> value = next.getValue();
            final MyColorButton value0 = value.getValue0();
            switch (next.getKey()) {
                case ColorsUtils.COLOR_TEXT_COLOR_NAME:
                    this.textColor = value0.getColor();
                    break;
                case ColorsUtils.COLOR_BACKGROUND_COLOR:
                    this.backgroundColor = value0.getColor();
                    break;
                default:
                    break;
            }
        }
        dispose();
    }

    private void onCancel() {
        dispose();
    }
}
