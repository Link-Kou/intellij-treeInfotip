package com.plugins.infotip.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlFile;
import com.plugins.infotip.gui.view.SelectColorIconsView;
import com.plugins.infotip.storage.XmlEntity;
import com.plugins.infotip.storage.XmlFileUtils;
import com.plugins.infotip.storage.XmlStorage;
import org.javatuples.Pair;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;

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
        dialog.setPreferredSize(new Dimension(380, 200));
        dialog.setSize(380, 200);
        dialog.setLocationRelativeTo(null);
        dialog.setResizable(false);
        //dialog.setMaximumSize(new Dimension(380, 250));
        dialog.setModal(true);

        XmlFileUtils.runActionType(anActionEvent, new XmlFileUtils.Callback() {
            @Override
            public void onModifyPath(List<Pair<String, String>> asBasePathOrExtension, List<XmlEntity> xmlEntitys, XmlFile fileDirectoryXml, Project project) {
                final XmlEntity xmlEntity = xmlEntitys.get(0);
                dialog.setIcons(xmlEntity.getIcon());
                dialog.setTextColor(xmlEntity.getTextColor());
                dialog.setBackgroundColor(xmlEntity.getBackgroundColor());
                dialog.setVisible(true);
                for (XmlEntity entity : xmlEntitys) {
                    entity.setIcon(dialog.getIcons());
                    entity.setTextColor(dialog.getTextColor());
                    entity.setBackgroundColor(dialog.getBackgroundColor());
                    XmlStorage.modify(project, fileDirectoryXml, entity);
                }
            }

            @Override
            public void onCreatePath(List<Pair<String, String>> asBasePathOrExtension, XmlFile fileDirectoryXml, Project project) {
                dialog.setVisible(true);
                for (Pair<String, String> pair : asBasePathOrExtension) {
                    final XmlEntity x = new XmlEntity().setPath(pair.getValue0());
                    x.setIcon(dialog.getIcons());
                    x.setTextColor(dialog.getTextColor());
                    x.setBackgroundColor(dialog.getBackgroundColor());
                    XmlStorage.create(project, fileDirectoryXml, x);
                }
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
