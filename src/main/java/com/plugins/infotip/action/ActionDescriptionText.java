package com.plugins.infotip.action;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.xml.XmlFile;
import com.plugins.infotip.storage.XmlEntity;
import com.plugins.infotip.storage.XmlFileUtils;
import com.plugins.infotip.storage.XmlStorage;
import org.jetbrains.annotations.NotNull;

/**
 * 右键菜单
 *
 * @author lk
 * @version 1.0
 * 2021/6/7 14:13
 */
public class ActionDescriptionText extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
        XmlFileUtils.runActionType(anActionEvent, new XmlFileUtils.Callback() {
            @Override
            public void onModifyPath(String asBasePath, XmlEntity x, XmlFile fileDirectoryXml, Project project, String extension) {
                String txt = Messages.showInputDialog(project, "Input Your " + asBasePath + "  Description", "What Needs To Be Description?", AllIcons.Actions.Menu_paste, x.getTitle(), null);
                if (null != txt) {
                    XmlStorage.modify(project, fileDirectoryXml, x.setTitle(txt));
                }
            }

            @Override
            public void onCreatePath(String asBasePath, XmlFile fileDirectoryXml, Project project, String extension) {
                String txt = Messages.showInputDialog(project, "Input Your " + asBasePath + "  Description", "What Needs To Be Description?", AllIcons.Actions.Menu_paste, "", null);
                if (null != txt) {
                    XmlStorage.create(project, fileDirectoryXml, new XmlEntity().setPath(asBasePath).setTitle(txt));
                }
            }
        });
    }

    /**
     * 项目构建完毕前就显示
     * 强烈建议不要覆盖,
     *
     * @return boolean
     */
    @Override
    public boolean isDumbAware() {
        return super.isDumbAware();
    }
}
