package com.plugins.infotip.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlFile;
import com.plugins.infotip.gui.view.SelectColorIconsView;
import com.plugins.infotip.storage.XmlEntity;
import com.plugins.infotip.storage.XmlFileUtils;
import com.plugins.infotip.storage.XmlStorage;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * A <code>ActionDescription</code> Class
 * 右键菜单
 *
 * @author lk
 * @version 1.0
 * 2021/6/7 14:13
 */
public class ActionDescriptionColorOrIcon extends AnAction {


    @Override
    public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
        final Project project = anActionEvent.getProject();
        if (null == project) {
            return;
        }
        final SelectColorIconsView dialog = new SelectColorIconsView();
        dialog.pack();
        dialog.setTitle("Select Icon Or Color");
        dialog.setSize(288, 120);
        dialog.setLocationRelativeTo(null);
        dialog.setResizable(false);
        Dimension dimension = new Dimension();
        dimension.setSize(288, 120);
        dialog.setMaximumSize(dimension);
        dialog.setModal(true);

        XmlFileUtils.runActionType(anActionEvent, new XmlFileUtils.Callback() {
            @Override
            public void onModifyPath(String asBasePath, XmlEntity x, XmlFile fileDirectoryXml, Project project, String extension) {
                dialog.setIcons(x.getIcon());
                dialog.setTextColor(x.getTextColor());
                dialog.setBackgroundColor(x.getBackgroundColor());
                dialog.setVisible(true);
                x.setIcon(dialog.getIcons());
                x.setTextColor(dialog.getTextColor());
                x.setBackgroundColor(dialog.getBackgroundColor());
                XmlStorage.modify(project, x);
            }

            @Override
            public void onCreatePath(String asBasePath, XmlFile fileDirectoryXml, Project project, String extension) {
                dialog.setVisible(true);
                final XmlEntity x = new XmlEntity()
                        .setPath(asBasePath);
                x.setIcon(dialog.getIcons());
                x.setTextColor(dialog.getTextColor());
                x.setBackgroundColor(dialog.getBackgroundColor());
                XmlStorage.create(fileDirectoryXml, project, x);
            }
        });
    }


    @Override
    public void update(@NotNull AnActionEvent event) {
        //在Action显示之前,根据选中文件扩展名判定是否显示此Action
        //this.getTemplatePresentation().setIcon(AllIcons.Actions.MenuPaste);
    }


    /**
     * 项目构建完毕前就显示
     *
     * @return boolean
     */
    @Override
    public boolean isDumbAware() {
        return super.isDumbAware();
    }


}
