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
import org.javatuples.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.List;

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
            public void onModifyPath(List<Pair<String, String>> asBasePathOrExtension, List<XmlEntity> xmlEntities, XmlFile fileDirectoryXml, Project project) {
                final XmlEntity xmlEntity = xmlEntities.get(0);
                String txt = Messages.showInputDialog(project, "Input Your Description", "What Needs To Be Description?", AllIcons.Actions.Menu_paste, xmlEntity.getTitle(), null);
                if (null != txt) {
                    for (XmlEntity x : xmlEntities) {
                        XmlStorage.modify(project, fileDirectoryXml, x.setTitle(txt));
                    }
                }
            }

            @Override
            public void onCreatePath(List<Pair<String, String>> asBasePathOrExtension, XmlFile fileDirectoryXml, Project project) {
                String txt = Messages.showInputDialog(project, "Input Your Description", "What Needs To Be Description?", AllIcons.Actions.Menu_paste, "", null);
                if (null != txt) {
                    for (Pair<String, String> pair : asBasePathOrExtension) {
                        XmlStorage.create(project, fileDirectoryXml, new XmlEntity().setPath(pair.getValue0()).setTitle(txt));
                    }
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
