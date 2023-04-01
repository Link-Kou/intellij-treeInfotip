package com.plugins.infotip;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlFile;
import com.plugins.infotip.state.FileDirectory;
import com.plugins.infotip.state.XmlEntity;
import com.plugins.infotip.state.XmlParsing;
import com.plugins.infotip.ui.Icons;
import com.plugins.infotip.ui.IconsList;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

import static com.plugins.infotip.state.FileDirectory.getBasePath;

/**
 * A <code>ActionDescription</code> Class
 * 右键菜单
 *
 * @author lk
 * @version 1.0
 * 2021/6/7 14:13
 */
public class ActionDescriptionIcon extends AnAction {


    @Override
    public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
        final Project project = anActionEvent.getProject();
        if (null == project) {
            return;
        }
        final IconsList dialog = new IconsList();
        dialog.pack();
        dialog.setTitle("Select Icons");
        dialog.setSize(288, 120);
        dialog.setLocationRelativeTo(null);
        dialog.setResizable(false);
        Dimension dimension = new Dimension();
        dimension.setSize(288, 120);
        dialog.setMaximumSize(dimension);
        dialog.setModal(true);
        getBasePath(anActionEvent, new FileDirectory.Callback() {
            @Override
            public void onModifyPath(String asbbasePath, XmlEntity x, XmlFile fileDirectoryXml, Project project, String extension) {
                dialog.setIcons(x.getIcon());
                dialog.setVisible(true);
                Icons icons = dialog.getIcons();
                XmlParsing.modifyPath(x.getTag(), icons, fileDirectoryXml, project);
            }

            @Override
            public void onCreatePath(String asbbasePath, XmlFile fileDirectoryXml, Project project, String extension) {
                dialog.setVisible(true);
                Icons icons = dialog.getIcons();
                XmlParsing.createPath(fileDirectoryXml, project, asbbasePath, null, icons, extension);
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
     * @return boolean
     */
    @Override
    public boolean isDumbAware() {
        return super.isDumbAware();
    }


}
