package com.plugins.infotip.gui.view;

import com.intellij.openapi.project.Project;
import com.intellij.ui.ColorChooserService;
import com.intellij.util.ui.EmptyIcon;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class SelectColorIconsView extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JComboBox<IconEntity> comboBox_icon;
    private JPanel jpanel_icon;
    private JPanel jpanel_text_color;
    private JPanel jpanel_background_color;

    /**
     * 图标下拉框里的「不设置」哨兵。
     *
     * <p>
     * 它不是 {@link IconsUtils} 里的一项（那边是纯反射 AllIcons 生成的，塞进去会污染
     * {@code findFitIcon} 的名字表），只存在于这个下拉框的 model 里。没有它就没有
     * 「清空图标」这个状态可停：以前一条新规则一点确定就必然写入一个 icon 属性。
     * </p>
     */
    private static final IconEntity NO_ICON = new IconEntity(EmptyIcon.ICON_16, "（不设置）");

    private String iconsName;
    private Color backgroundColor;

    private Color textColor;

    /**
     * 只用来传给颜色选择器，让它拿到项目上下文；允许为 null
     */
    private final Project project;

    Map<String, Pair<MyColorButton, Color>> mapColor = new HashMap<String, Pair<MyColorButton, Color>>() {{
        put(ColorsUtils.COLOR_TEXT_COLOR_NAME, null);
        put(ColorsUtils.COLOR_BACKGROUND_COLOR, null);
    }};

    public SelectColorIconsView(Project project) {
        this.project = project;
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(buttonOK);


        //region 自定义组件
        MyComboBoxRenderer myComboBoxRenderer = new MyComboBoxRenderer();
        comboBox_icon.setRenderer(myComboBoxRenderer);
        //哨兵排在最前，顺便让下拉框的默认选中项变成「不设置」
        comboBox_icon.addItem(NO_ICON);
        ArrayList<IconEntity> allIcons = IconsUtils.getAllIcons();
        for (IconEntity allIcon : allIcons) {
            comboBox_icon.addItem(allIcon);
        }
        final JButton clearIconButton = createClearButton();
        clearIconButton.setToolTipText("不设图标，用回文件类型自带的那个");
        clearIconButton.addActionListener(e -> comboBox_icon.setSelectedItem(NO_ICON));
        jpanel_icon.add(clearIconButton, gapAfterControl());

        final Iterator<Map.Entry<String, Pair<MyColorButton, Color>>> iterator = mapColor.entrySet().iterator();
        while (iterator.hasNext()) {
            final Map.Entry<String, Pair<MyColorButton, Color>> next = iterator.next();
            final Pair<MyColorButton, Color> value = next.getValue();
            if (null == value) {
                final MyColorButton myColorButton = new MyColorButton(null);
                myColorButton.addActionListener(e -> onColor(next.getKey(), myColorButton));
                final JButton clearButton = createClearButton();
                clearButton.setToolTipText("清掉这个颜色");
                clearButton.addActionListener(e -> clearColor(next.getKey(), myColorButton));
                switch (next.getKey()) {
                    case ColorsUtils.COLOR_TEXT_COLOR_NAME:
                        this.jpanel_text_color.add(myColorButton);
                        this.jpanel_text_color.add(clearButton, gapAfterControl());
                        break;
                    case ColorsUtils.COLOR_BACKGROUND_COLOR:
                        this.jpanel_background_color.add(myColorButton);
                        this.jpanel_background_color.add(clearButton, gapAfterControl());
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

    /**
     * 三行各一个的「清除」小按钮。外面那个面板是 GridBagLayout，控件都是紧跟在前一个后面排的，
     * 这里只负责把外边距和焦点行为补齐。
     */
    private static JButton createClearButton() {
        final JButton button = new JButton("清除");
        button.setMargin(new Insets(2, 8, 2, 8));
        //不抢焦点，免得一进弹窗默认按钮就跑到「清除」上
        button.setFocusable(false);
        return button;
    }

    /**
     * 贴着前一个控件、留一点横向间隔。GridBagLayout 的默认约束就是「接在最后一个后面」，
     * 所以只改 insets 就够。
     */
    private static GridBagConstraints gapAfterControl() {
        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(0, 6, 0, 0);
        return constraints;
    }

    /**
     * 清掉某一路颜色：色块回到「未设置」，同时把 mapColor 里记的值也置空，
     * 这样 {@link #onOK()} 取到的是 null，写回 XML 时属性会被删掉而不是留空串。
     */
    private void clearColor(String type, MyColorButton myColorButton) {
        myColorButton.setColor(null);
        myColorButton.repaint();
        this.mapColor.put(type, Pair.with(myColorButton, null));
    }


    private void onColor(String type, MyColorButton myColorButton) {
        //原来用的 com.intellij.ui.ColorChooser 已标 @ApiStatus.ScheduledForRemoval，
        //换成 ColorChooserService（2022.3 就有）。
        //必须用带 Project 的 7 参重载：不带 Project 的 6 参那个在 2022.3 就已经
        //@Deprecated forRemoval（虽然 2026.2 又不带标记了），编译期会报 [removal] 告警。
        //7 参这个两个版本都是干净的。参数要写全，可省的那两个是 Kotlin 默认值，
        //Java 侧只有 6 参和 7 参两个签名；后两个与原来 chooseColor(c, caption, color, true)
        //的默认值一致——监听器列表为空、opacityInPercent=false。
        //project 传 null 也合法（2022.3 的形参没标 @NotNull，2026.2 的 Kotlin 实现
        //也只对 parent 和 listeners 做非空检查）。
        Color newColor = ColorChooserService.getInstance()
                .showDialog(this.project, this, "选择颜色", Color.white, true, Collections.emptyList(), false);
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
        //先回到「不设置」：配置里本来没有图标，或者那个名字在新版 AllIcons 里已经没了
        //（反射拿不到就查不到），都必须停在这里。停在别处的话一点确定就把别人的图标写回去了。
        comboBox_icon.setSelectedItem(NO_ICON);
        this.iconsName = null;
        for (IconEntity allIcon : IconsUtils.getAllIcons()) {
            String name1 = allIcon.getName();
            if (null != name1) {
                if (name1.equals(name)) {
                    this.iconsName = name;
                    comboBox_icon.setSelectedItem(allIcon);
                    break;
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
        //哨兵（或者压根没选中）等于不设图标，写回 null，XmlStorage 会把 icon 属性删掉
        if (null == selectedItem || NO_ICON == selectedItem) {
            this.iconsName = null;
        } else {
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
