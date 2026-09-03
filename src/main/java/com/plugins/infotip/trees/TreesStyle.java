package com.plugins.infotip.trees;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleTextAttributes;
import com.plugins.infotip.gui.IconsUtils;
import com.plugins.infotip.storage.XmlEntity;
import com.plugins.infotip.gui.ColorsUtils;

import javax.swing.*;
import java.awt.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A <code>TreesStyle</code> Class
 *
 * @author lk
 * @version 1.0
 * <p><b>date: 2023/4/13 14:47</b></p>
 */
public class TreesStyle {

    private static final Map<Object, Callback> callbackList = new ConcurrentHashMap<Object, Callback>();

    public interface Callback {
        void change();
    }

    public static void ListenerStyle(Object id, Callback callback) {
        callbackList.put(id, callback);
    }

    public static void setStyle(final AbstractTreeNode<?> abstractTreeNode) {
        final VirtualFile virtualFile = TreesUtils.getVirtualFile(abstractTreeNode);
        final String name = abstractTreeNode.getName();
        final PresentationData presentation = abstractTreeNode.getPresentation();
        final XmlEntity matchPath = TreesUtils.getMatchPath(virtualFile, abstractTreeNode.getProject());
        setStyle(presentation, matchPath, name);
    }

    public static void setStyle(final AbstractTreeNode<?> node, PresentationData presentation) {
        final VirtualFile virtualFile = TreesUtils.getVirtualFile(node);
        final String name = node.getName();
        final XmlEntity matchPath = TreesUtils.getMatchPath(virtualFile, node.getProject());
        setStyle(presentation, matchPath, name);
    }


    /**
     * 设置样式
     *
     * @param presentation 样式对象
     * @param xmlEntity    节点对象
     * @param name         节点名称
     */
    public static void setStyle(final PresentationData presentation, XmlEntity xmlEntity, String name) {
        if (null == presentation) {
            return;
        }
        if (null == xmlEntity) {
            //presentation.clear();
            return;
        }
        //设置图标:AllIcons 里有一批图标长边大于 16,直接用会把树的行高撑起来,统一缩过再设
        final Icon icon = IconsUtils.findFitIcon(xmlEntity.getIcon());
        if (null != icon) {
            presentation.setIcon(icon);
        }
        //设置锚定文本
        presentation.setLocationString(xmlEntity.getTitle());
        //设置悬浮提示
        if (isNotEmpty(xmlEntity.getTooltipTitle())) {
            presentation.setTooltip(xmlEntity.getTooltipTitle());
        }
        //覆盖节点显示名,为空时沿用节点原本的名称
        final boolean hasPresentableText = isNotEmpty(xmlEntity.getPresentableText());
        final String displayName = hasPresentableText ? xmlEntity.getPresentableText() : name;
        if (hasPresentableText) {
            presentation.setPresentableText(displayName);
        }
        final Color backgroundColor = ColorsUtils.toColor(xmlEntity.getBackgroundColor());
        final Color textColor = ColorsUtils.toColor(xmlEntity.getTextColor());
        final boolean strikethrough = xmlEntity.isStrikethroughEnabled();
        if (null != textColor || strikethrough || hasPresentableText) {
            //设置文本颜色与删除线,两者互不依赖且可叠加:
            //只设颜色时用 PLAIN + textColor;只设删除线时用 STRIKEOUT + null(沿用主题前景色);
            //两者都设时用 STRIKEOUT + textColor。
            final int style = strikethrough
                    ? SimpleTextAttributes.STYLE_STRIKEOUT
                    : SimpleTextAttributes.STYLE_PLAIN;
            presentation.clearText();
            presentation.addText(displayName, new SimpleTextAttributes(style, textColor));
        }
        if (null != backgroundColor) {
            //设置背景色
            presentation.setBackground(backgroundColor);
        }
        for (Map.Entry<Object, Callback> objectCallbackEntry : callbackList.entrySet()) {
            final Callback value = objectCallbackEntry.getValue();
            value.change();
        }
    }

    private static boolean isNotEmpty(String value) {
        return null != value && !value.trim().isEmpty();
    }
}
