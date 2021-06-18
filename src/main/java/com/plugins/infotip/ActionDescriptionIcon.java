package com.plugins.infotip;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.xml.XmlFile;
import com.plugins.infotip.ui.Icons;
import com.plugins.infotip.ui.IconsList;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

import static com.plugins.infotip.FileDirectory.getBasePath;

/**
 * A <code>ActionDescription</code> Class
 * 右键菜单
 *
 * @author lk
 * @version 1.0
 * @date 2021/6/7 14:13
 */
public class ActionDescriptionIcon extends AnAction {


    @Override
    public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
        IconsList dialog = new IconsList();
        dialog.pack();
        dialog.setTitle("Select Icons");
        dialog.setSize(288, 100);
        dialog.setLocationRelativeTo(null);
        dialog.setResizable(false);
        Dimension dimension = new Dimension();
        dimension.setSize(288, 100);
        dialog.setMaximumSize(dimension);
        dialog.setModal(true);
        dialog.setVisible(true);
        Icons icons = dialog.getIcons();
        if (null != icons) {
            getBasePath(anActionEvent, new FileDirectory.Callback() {
                @Override
                public void onModifyPath(String asbbasePath, XmlEntity x, XmlFile fileDirectoryXml, Project project, String extension) {
                    XmlParsing.modifyPath(x.getTag(), null, icons, fileDirectoryXml, project);
                }

                @Override
                public void onCreatePath(String asbbasePath, XmlFile fileDirectoryXml, Project project, String extension) {
                    XmlParsing.createPath(fileDirectoryXml, project, asbbasePath, null, icons, extension);
                }
            });
        }
    }


    @Override
    public void update(@NotNull AnActionEvent event) {
        //在Action显示之前,根据选中文件扩展名判定是否显示此Action
        //this.getTemplatePresentation().setIcon(AllIcons.Actions.MenuPaste);
    }


}
