package com.plugins.infotip;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlFile;
import com.plugins.infotip.state.FileDirectory;
import com.plugins.infotip.state.XmlEntity;
import com.plugins.infotip.state.XmlParsing;
import com.plugins.infotip.ui.ColorList;
import com.plugins.infotip.ui.Icons;
import com.plugins.infotip.ui.IconsList;
import com.plugins.infotip.ui.compone.MyColorButton;
import org.javatuples.Pair;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Map;

import static com.plugins.infotip.state.FileDirectory.getBasePath;

/**
 * A <code>ActionDescription</code> Class
 * 右键菜单
 *
 * @author lk
 * @version 1.0
 * 2021/6/7 14:13
 */
public class ActionDescriptionColor extends AnAction {


    @Override
    public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
        final Project project = anActionEvent.getProject();
        if (null == project) {
            return;
        }
        final ColorList dialog = new ColorList(project);
        dialog.pack();
        dialog.setTitle("Select Color");
        dialog.setSize(235, 135);
        dialog.setLocationRelativeTo(null);
        dialog.setResizable(false);
        Dimension dimension = new Dimension();
        dimension.setSize(288, 120);
        dialog.setMaximumSize(dimension);
        dialog.setModal(true);
        getBasePath(anActionEvent, new FileDirectory.Callback() {
            @Override
            public void onModifyPath(String asbbasePath, XmlEntity x, XmlFile fileDirectoryXml, Project project, String extension) {
                dialog.setColor(x.getColor());
                dialog.setVisible(true);
                final Map<String, Color> color = dialog.getColor();
                XmlParsing.modifyPath(x.getTag(), color, fileDirectoryXml, project);
            }

            @Override
            public void onCreatePath(String asbbasePath, XmlFile fileDirectoryXml, Project project, String extension) {
                dialog.setVisible(true);
                final Map<String, Color> color = dialog.getColor();
                XmlParsing.createPath(fileDirectoryXml, project, asbbasePath, null, null, color, extension);
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
