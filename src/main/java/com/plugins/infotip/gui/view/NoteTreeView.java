package com.plugins.infotip.gui.view;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.treeStructure.Tree;
import com.plugins.infotip.PluginStartupActivity;
import com.plugins.infotip.gui.IconsUtils;
import com.plugins.infotip.gui.compone.MyTreeNode;
import com.plugins.infotip.storage.XmlEntity;
import com.plugins.infotip.storage.XmlFileUtils;
import com.plugins.infotip.storage.XmlStorage;
import com.plugins.infotip.trees.TreesUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * A <code>NoteTreeView</code> Class
 *
 * @author lk
 * @version 1.0
 * <p><b>date: 2023/4/13 21:03</b></p>
 */
public class NoteTreeView extends Tree implements ToolWindowFactory {

    public NoteTreeView() {
        super(new DefaultMutableTreeNode("备注列表"));
    }

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        final NoteTreeView noteTreeView = new NoteTreeView();
        noteTreeView.setCellRenderer(new NoteCellRenderer());
        XmlFileUtils.SaveCallback saveCallback = () -> {
            DefaultTreeModel model = (DefaultTreeModel) noteTreeView.getModel();
            final DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
            root.removeAllChildren();
            final List<XmlEntity> xmlEntity = XmlStorage.getXmlEntity(project);
            if (null != xmlEntity) {
                for (XmlEntity entity : xmlEntity) {
                    root.add(buildNode(project, entity));
                }
            }
            //reload 要放在加完子节点之后：root.add 走的是 DefaultMutableTreeNode 自己的方法，
            //不发 model 事件，先 reload 再 add 的话新节点得等下一次重绘才出得来。
            model.reload();
            noteTreeView.expandPath(new TreePath(root));
        };
        PluginStartupActivity.RunCallback runCallback = saveCallback::run;
        noteTreeView.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getClickCount() != 2) {
                    return;
                }
                final Object component = noteTreeView.getLastSelectedPathComponent();
                if (component instanceof MyTreeNode) {
                    final Object userEntity = ((MyTreeNode) component).getUserEntity();
                    if (userEntity instanceof XmlEntity) {
                        TreesUtils.Navigation(project, ((XmlEntity) userEntity).getPath());
                    }
                } else {
                    //双击根节点重建整个列表。路径存不存在只在建节点时查一次，
                    //在 IDE 外面删掉文件不会有 PSI 事件，就靠这里手动刷。
                    saveCallback.run();
                }
            }
        });
        PluginStartupActivity.ListenerRun(project, runCallback);
        XmlFileUtils.ListenerSave(project, saveCallback);
        saveCallback.run();
        final ContentFactory contentFactory = ContentFactory.getInstance();
        //必须套一层滚动面板，否则备注条数超过工具窗口高度时只能看到前几条，滚不动
        Content content = contentFactory.createContent(new JBScrollPane(noteTreeView), "", false);
        toolWindow.getContentManager().addContent(content);
    }

    /**
     * 建一个节点，同时把图标和路径失效状态算好存进去
     * <p>
     * 图标表示这条规则作用在什么上（目录 / 哪类文件 / 路径已失效），不是用户自己配的那个图标——
     * 配的图标在项目树上已经看得到，摆这里反而会盖掉目录和文件的区分。
     * </p>
     * <p>
     * 存不存在只在这里查一次，不放渲染器里：渲染器每帧对每个可见行都要调一次，不能碰 VFS。
     * </p>
     */
    private static MyTreeNode buildNode(Project project, XmlEntity entity) {
        final MyTreeNode node = new MyTreeNode(label(entity));
        node.setUserEntity(entity);
        final String extension = entity.getExtension();
        final boolean typeRule = null != extension && !extension.trim().isEmpty();
        final String path = entity.getPath();
        //只写 extension 的全项目规则没有路径可查，永远算有效
        if (typeRule && (null == path || path.trim().isEmpty())) {
            return node.setIcon(extensionIcon(extension));
        }
        final VirtualFile file = TreesUtils.findProjectFile(project, path);
        if (null == file) {
            return node.setMissing(true).setIcon(fit(AllIcons.General.Error));
        }
        if (typeRule) {
            //目录级类型规则：路径是限定目录，图标按它管的那类文件给
            return node.setIcon(extensionIcon(extension));
        }
        if (file.isDirectory()) {
            return node.setIcon(fit(AllIcons.Nodes.Folder));
        }
        return node.setIcon(fit(FileTypeManager.getInstance().getFileTypeByFileName(file.getName()).getIcon()));
    }

    /**
     * 扩展名对应的文件类型图标，认不出来的扩展名会落到 UnknownFileType 的图标
     */
    private static Icon extensionIcon(String extension) {
        return fit(FileTypeManager.getInstance().getFileTypeByExtension(extension.trim()).getIcon());
    }

    /**
     * 统一缩到 16。{@code AllIcons} 里有 32×15、18×22 这种，不缩会把列表行高撑起来
     */
    private static Icon fit(Icon icon) {
        return null == icon ? AllIcons.FileTypes.Any_type : IconsUtils.fit(icon);
    }

    /**
     * 列表里显示的文字
     * <p>
     * 类型规则不绑定单个文件，只显示备注看不出它管的是什么，所以补上扩展名和生效范围。
     * </p>
     */
    private static String label(XmlEntity entity) {
        final String title = null == entity.getTitle() ? "" : entity.getTitle();
        final String extension = entity.getExtension();
        if (null == extension || extension.trim().isEmpty()) {
            return title;
        }
        final String path = entity.getPath();
        final String scope = null == path || path.trim().isEmpty() ? "整个项目" : path;
        final String suffix = "*." + extension.trim() + " @ " + scope;
        return title.isEmpty() ? suffix : title + "  [" + suffix + "]";
    }

    /**
     * 备注前面画类型图标，指向的路径已经不存在的整行标红
     */
    private static class NoteCellRenderer extends ColoredTreeCellRenderer {

        @Override
        public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected,
                                          boolean expanded, boolean leaf, int row, boolean hasFocus) {
            if (!(value instanceof MyTreeNode)) {
                //根节点，顺手告诉用户双击可以重新检查路径
                if (value instanceof DefaultMutableTreeNode) {
                    append(String.valueOf(((DefaultMutableTreeNode) value).getUserObject()));
                    append("  (双击刷新)", SimpleTextAttributes.GRAYED_ATTRIBUTES);
                }
                return;
            }
            final MyTreeNode node = (MyTreeNode) value;
            setIcon(node.getIcon());
            final String text = String.valueOf(node.getUserObject());
            if (node.isMissing()) {
                append(text, SimpleTextAttributes.ERROR_ATTRIBUTES);
                append("  路径已失效", SimpleTextAttributes.GRAYED_ATTRIBUTES);
            } else {
                append(text, SimpleTextAttributes.REGULAR_ATTRIBUTES);
            }
        }
    }
}
