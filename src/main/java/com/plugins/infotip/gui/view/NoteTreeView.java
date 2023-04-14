package com.plugins.infotip.gui.view;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;
import com.intellij.ui.treeStructure.Tree;
import com.plugins.infotip.gui.compone.MyTreeNode;
import com.plugins.infotip.storage.XmlEntity;
import com.plugins.infotip.storage.XmlStorage;
import com.plugins.infotip.trees.TreesStyle;
import com.plugins.infotip.trees.TreesUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Enumeration;
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
        super(new DefaultMutableTreeNode("Remarks"));
    }

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        NoteTreeView noteTreeView = new NoteTreeView();
        final DefaultMutableTreeNode root = (DefaultMutableTreeNode) noteTreeView.getModel().getRoot();
        noteTreeView.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    final MyTreeNode lastSelectedPathComponent = (MyTreeNode) noteTreeView.getLastSelectedPathComponent();
                    final XmlEntity userEntity = (XmlEntity) lastSelectedPathComponent.getUserEntity();
                    if (null != userEntity) {
                        TreesUtils.Navigation(project, userEntity.getPath());
                    }
                }
            }
        });
        TreesStyle.ListenerStyle(project, () -> {
            root.removeAllChildren();
            final List<XmlEntity> xmlEntity = XmlStorage.getXmlEntity(project);
            if (null != xmlEntity) {
                for (XmlEntity entity : xmlEntity) {
                    final MyTreeNode defaultMutableTreeNode = new MyTreeNode(entity.getTitle());
                    defaultMutableTreeNode.setUserEntity(entity);
                    root.add(defaultMutableTreeNode);
                    for (int i = 0; i < root.getChildCount(); i++) {
                        noteTreeView.expandRow(i);
                    }
                }
            }
        });
        final ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
        Content content = contentFactory.createContent(noteTreeView, "", false);
        toolWindow.getContentManager().addContent(content);
    }

}
