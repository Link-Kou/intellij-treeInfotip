package com.plugins.infotip.action;

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
 * 右键菜单：覆盖节点显示名称
 * <p>
 * 与「添加文字备注」不同，这里改的是节点自身的名字，留空则恢复成文件的真实名称。
 * </p>
 *
 * @author lk
 * @version 1.0
 */
public class ActionDescriptionPresentableText extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
        XmlFileUtils.runActionType(anActionEvent, new XmlFileUtils.Callback() {
            @Override
            public void onModifyPath(List<Pair<String, String>> asBasePathOrExtension, List<XmlEntity> xmlEntities, XmlFile fileDirectoryXml, Project project) {
                final XmlEntity xmlEntity = xmlEntities.get(0);
                String txt = Messages.showInputDialog(project, "请输入要显示的名称，留空则恢复文件原名", "覆盖显示名称", Messages.getQuestionIcon(), xmlEntity.getPresentableText(), null);
                if (null != txt) {
                    for (XmlEntity x : xmlEntities) {
                        XmlStorage.modify(project, fileDirectoryXml, x.setPresentableText(txt));
                    }
                }
            }

            @Override
            public void onCreatePath(List<Pair<String, String>> asBasePathOrExtension, XmlFile fileDirectoryXml, Project project) {
                String txt = Messages.showInputDialog(project, "请输入要显示的名称，留空则恢复文件原名", "覆盖显示名称", Messages.getQuestionIcon(), "", null);
                if (null != txt) {
                    for (Pair<String, String> pair : asBasePathOrExtension) {
                        XmlStorage.create(project, fileDirectoryXml, new XmlEntity().setPath(pair.getValue0()).setPresentableText(txt));
                    }
                }
            }
        });
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
