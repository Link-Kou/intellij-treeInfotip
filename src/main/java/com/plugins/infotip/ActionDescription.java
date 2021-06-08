package com.plugins.infotip;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE;

/**
 * A <code>ActionDescription</code> Class
 * 右键菜单
 *
 * @author lk
 * @version 1.0
 * @date 2021/6/7 14:13
 */
public class ActionDescription extends AnAction {


    @Override
    public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
        Project project = anActionEvent.getProject();
        if (null == project) {
            return;
        }
        XmlFile fileDirectoryXml = FileDirectory.getFileDirectoryXml(project, true);
        List<XmlEntity> refreshXml = XmlParsing.getRefreshXml(project, fileDirectoryXml);
        //获取文件、文件夹等对象
        VirtualFile file = VIRTUAL_FILE.getData(anActionEvent.getDataContext());
        if (null != file) {
            //使用相对路径
            String basePath = project.getPresentableUrl();
            if (null == basePath) {
                return;
            }
            String presentableUrl = file.getPresentableUrl();
            String asbbasePath = presentableUrl.replace(basePath, "");
            String extension = file.getExtension();
            boolean notfind = false;
            String txt = null;
            for (XmlEntity x : refreshXml) {
                if (presentableUrl.equals(x.getPath())) {
                    txt = Messages.showInputDialog(project, "Input Your " + asbbasePath + "  Description",
                            "What Needs To Be Description?", AllIcons.Actions.MenuPaste, x.getTitle(), null);
                    notfind = true;
                    if (null != txt) {
                        XmlParsing.modifyPath(x.getTag(), txt, project);
                    }
                    break;
                }
            }
            if (!notfind) {
                txt = Messages.showInputDialog(project, "Input Your " + asbbasePath + "  Description",
                        "What Needs To Be Description?", AllIcons.Actions.MenuPaste, "", null);
                if (null != txt) {
                    XmlParsing.createPath(fileDirectoryXml, project, asbbasePath, txt, extension);
                }
            }
        }
    }


    @Override
    public void update(@NotNull AnActionEvent event) {
        //在Action显示之前,根据选中文件扩展名判定是否显示此Action
        //this.getTemplatePresentation().setIcon(AllIcons.Actions.MenuPaste);
    }


}
