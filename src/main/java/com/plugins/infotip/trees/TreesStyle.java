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
        setStyle(presentation, matchPath, name, hasVcsColor(abstractTreeNode));
    }

    public static void setStyle(final AbstractTreeNode<?> node, PresentationData presentation) {
        final VirtualFile virtualFile = TreesUtils.getVirtualFile(node);
        final String name = node.getName();
        final XmlEntity matchPath = TreesUtils.getMatchPath(virtualFile, node.getProject());
        setStyle(presentation, matchPath, name, hasVcsColor(node));
    }


    /**
     * 这个节点平台会不会给它上 VCS 状态色（蓝=改过、绿=已加、红=未纳入版本控制）。
     *
     * <p>
     * 判据要和平台一致：{@code AbstractTreeNode.postprocess} 把状态色写进
     * {@code PresentationData.forcedTextForeground}，而渲染时 {@code NodeRenderer} 只把它刷到
     * <b>自己没有指定前景色</b>的文本片段上。我们只要不给 fgColor，状态色自己就会上去。
     * 目录在 postprocess 里走的是 {@code getRecursiveStatus}（子节点有改动就标蓝），
     * {@link AbstractTreeNode#getFileStatusColor} 里包的就是这套，直接问它最准。
     * </p>
     *
     * <p>
     * 唯一的漏网之鱼是节点被 Ctrl+X 剪切时的 CUT_COLOR：判据用的 {@code valueIsCut()} 是
     * protected，外面问不到。剪切是转瞬即逝的状态，不单为它绕路。
     * </p>
     */
    private static boolean hasVcsColor(AbstractTreeNode<?> node) {
        return null != node.getFileStatusColor(node.getFileStatus());
    }


    /**
     * 设置样式
     *
     * @param presentation    样式对象
     * @param xmlEntity       节点对象
     * @param name            节点名称
     * @param hasVcsColor     平台会给这个节点上 VCS 状态色，文字颜色要让位
     */
    private static void setStyle(final PresentationData presentation, XmlEntity xmlEntity, String name, boolean hasVcsColor) {
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
        presentation.setLocationString(xmlEntity.getNote());
        //设置悬浮提示
        if (isNotEmpty(xmlEntity.getTooltip())) {
            presentation.setTooltip(xmlEntity.getTooltip());
        }
        //覆盖节点显示名,为空时沿用节点原本的名称
        final boolean hasPresentableText = isNotEmpty(xmlEntity.getLabel());
        final String displayName = hasPresentableText ? xmlEntity.getLabel() : name;
        if (hasPresentableText) {
            presentation.setPresentableText(displayName);
        }
        final Color backgroundColor = ColorsUtils.toColor(xmlEntity.getBg());
        //有 VCS 状态色的节点让状态色优先：fgColor 留 null，平台渲染时会把 forcedTextForeground
        //刷到这个片段上（见 hasVcsColor）。配置里的颜色不删也不改——文件回到「无改动」时它会自己回来。
        //图标、背景色、锚定文本都还在，所以让位的只是文字颜色这一条通道。
        final Color textColor = hasVcsColor ? null : ColorsUtils.toColor(xmlEntity.getColor());
        final boolean strikethrough = xmlEntity.isStrikeEnabled();
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
