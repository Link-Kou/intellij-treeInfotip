package com.plugins.infotip;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.xml.XmlFile;
import com.plugins.infotip.state.FileDirectory;
import com.plugins.infotip.state.XmlEntity;
import com.plugins.infotip.state.XmlParsing;
import org.jetbrains.annotations.NotNull;

import static com.plugins.infotip.state.FileDirectory.getBasePath;

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
        getBasePath(anActionEvent, new FileDirectory.Callback() {
            @Override
            public void onModifyPath(String asbbasePath, XmlEntity x, XmlFile fileDirectoryXml, Project project, String extension) {
                String txt = Messages.showInputDialog(project, "Input Your " + asbbasePath + "  Description",
                        "What Needs To Be Description?", AllIcons.Actions.Menu_paste, x.getTitle(), null);
                if (null != txt) {
                    XmlParsing.modifyPath(x.getTag(), txt, fileDirectoryXml, project);
                }
            }

            @Override
            public void onCreatePath(String asbbasePath, XmlFile fileDirectoryXml, Project project, String extension) {
                String txt = Messages.showInputDialog(project, "Input Your " + asbbasePath + "  Description",
                        "What Needs To Be Description?", AllIcons.Actions.Menu_paste, "", null);
                if (null != txt) {
                    XmlParsing.createPath(fileDirectoryXml, project, asbbasePath, txt, null, null, extension);
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
