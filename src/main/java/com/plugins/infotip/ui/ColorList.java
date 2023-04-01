package com.plugins.infotip.ui;

import com.intellij.openapi.project.Project;
import com.intellij.ui.ColorChooser;
import com.plugins.infotip.ui.compone.MyColorButton;
import org.javatuples.Pair;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class ColorList extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JPanel jpanel_textcolor;
    private JPanel jpanel_backgroundcolor;

    Map<String, Pair<MyColorButton, Color>> mapColor = new HashMap<String, Pair<MyColorButton, Color>>() {{
        put("textcolor", null);
        put("backgroundcolor", null);
    }};

    public ColorList(Project project) {
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(buttonOK);

        //region 自定义控件
        final Iterator<Map.Entry<String, Pair<MyColorButton, Color>>> iterator = mapColor.entrySet().iterator();
        while (iterator.hasNext()) {
            final Map.Entry<String, Pair<MyColorButton, Color>> next = iterator.next();
            final Pair<MyColorButton, Color> value = next.getValue();
            if (null == value) {
                final MyColorButton myColorButton = new MyColorButton(null);
                myColorButton.addActionListener(e -> onColor(project, next.getKey(), myColorButton));
                switch (next.getKey()) {
                    case "textcolor":
                        this.jpanel_textcolor.add(myColorButton);
                        break;
                    case "backgroundcolor":
                        this.jpanel_backgroundcolor.add(myColorButton);
                        break;
                    default:
                        break;
                }
                mapColor.put(next.getKey(), Pair.with(myColorButton, null));
            }
        }
        //endregion

        buttonOK.addActionListener(e -> onOK());
        buttonCancel.addActionListener(e -> onCancel());
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

    private void onColor(Project project, String type, MyColorButton myColorButton) {
        Color newColor = ColorChooser.chooseColor(this, "Choose Color", Color.white, true);
        if (null != newColor) {
            myColorButton.setColor(newColor);
            this.mapColor.put(type, Pair.with(myColorButton, newColor));
        }
    }

    public Map<String, Color> getColor() {
        final Iterator<Map.Entry<String, Pair<MyColorButton, Color>>> iterator = mapColor.entrySet().iterator();
        Map<String, Color> colorMap = new HashMap<String, Color>();
        while (iterator.hasNext()) {
            final Map.Entry<String, Pair<MyColorButton, Color>> next = iterator.next();
            colorMap.put(next.getKey(), next.getValue().getValue1());
        }
        return colorMap;
    }

    public void setColor(String colorlist) {
        final Map<String, Color> colorMap = Colors.toMapColor(colorlist);
        if (null == colorMap) {
            return;
        }
        final Iterator<Map.Entry<String, Pair<MyColorButton, Color>>> iterator = mapColor.entrySet().iterator();
        while (iterator.hasNext()) {
            final Map.Entry<String, Pair<MyColorButton, Color>> next = iterator.next();
            final Color color = colorMap.get(next.getKey());
            final Pair<MyColorButton, Color> value = next.getValue();
            final MyColorButton value0 = value.getValue0();
            value0.setColor(color);
            mapColor.put(next.getKey(), Pair.with(value0, color));
        }
    }

    private void onOK() {
        // add your code here
        dispose();
    }

    private void onCancel() {
        // add your code here if necessary
        dispose();
    }
}
