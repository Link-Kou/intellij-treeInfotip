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
        final Project project = anActionEvent.getProject();
        if (null == project) {
            return;
        }
        //获取文件、文件夹等对象
        VirtualFile file = VIRTUAL_FILE.getData(anActionEvent.getDataContext());
        if (null != file) {
            XmlFile fileDirectoryXml = FileDirectory.getFileDirectoryXml(project, true);
            List<XmlEntity> refreshXml = XmlParsing.getRefreshXml(project, fileDirectoryXml);
            //使用相对路径
            String basePath = project.getPresentableUrl();
            if (null != basePath && basePath.length() > 0) {
                //改为安长度去除
                String presentableUrl = file.getPresentableUrl();
                if (presentableUrl.length() < basePath.length()) {
                    Messages.showMessageDialog(project, "Unable to get the root path of the file", "Can't Get Path", AllIcons.Actions.Menu_paste);
                    return;
                }
                String asbbasePath = presentableUrl.substring(basePath.length(), presentableUrl.length());
                String extension = file.getExtension();
                boolean notfind = false;
                String txt = null;
                for (XmlEntity x : refreshXml) {
                    if (presentableUrl.equals(x.getPath())) {
                        txt = Messages.showInputDialog(project, "Input Your " + asbbasePath + "  Description",
                                "What Needs To Be Description?", AllIcons.Actions.Menu_paste, x.getTitle(), null);
                        notfind = true;
                        if (null != txt) {
                            XmlParsing.modifyPath(x.getTag(), txt, fileDirectoryXml,project);
                        }
                        break;
                    }
                }
                if (!notfind) {
                    txt = Messages.showInputDialog(project, "Input Your " + asbbasePath + "  Description",
                            "What Needs To Be Description?", AllIcons.Actions.Menu_paste, "", null);
                    if (null != txt) {
                        XmlParsing.createPath(fileDirectoryXml, project, asbbasePath, txt, extension);
                    }
                }
            } else {
                Messages.showMessageDialog(project, "Unable to get the root path of the project", "Can't Get Path", AllIcons.Actions.Menu_paste);
            }
        }
    }


    @Override
    public void update(@NotNull AnActionEvent event) {
        //在Action显示之前,根据选中文件扩展名判定是否显示此Action
        //this.getTemplatePresentation().setIcon(AllIcons.Actions.MenuPaste);
    }


}
