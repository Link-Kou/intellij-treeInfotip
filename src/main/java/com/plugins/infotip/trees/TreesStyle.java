package com.plugins.infotip.trees;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleTextAttributes;
import com.plugins.infotip.gui.IconsUtils;
import com.plugins.infotip.storage.XmlEntity;
import com.plugins.infotip.gui.ColorsUtils;
import com.plugins.infotip.gui.entity.IconEntity;

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
        if (null == presentation || null == xmlEntity) {
            return;
        }
        //设置图标
        for (IconEntity allIcon : IconsUtils.getAllIcons()) {
            if (allIcon.getName().equals(xmlEntity.getIcon())) {
                presentation.setIcon(allIcon.getIcon());
            }
        }
        //设置锚定文本
        presentation.setLocationString(xmlEntity.getTitle());
        final Color backgroundColor = ColorsUtils.toColor(xmlEntity.getBackgroundColor());
        final Color textColor = ColorsUtils.toColor(xmlEntity.getTextColor());
        if (null != textColor) {
            //设置文本颜色
            presentation.clearText();
            presentation.addText(name, new SimpleTextAttributes(0, textColor));
        }
        if (null != backgroundColor) {
            //设置背景色
            presentation.setBackground(backgroundColor);
        }
        for (Map.Entry<Object, Callback> objectCallbackEntry : callbackList.entrySet()) {
            final Callback value = objectCallbackEntry.getValue();
            value.change();
        }
        //设置节点本身文本
        //presentation.setPresentableText(matchPath.getTitle());
        //设置提示
        //presentation.setTooltip(matchPath.getTitle());
    }
}
