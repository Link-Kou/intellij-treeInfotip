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
 * 右键菜单：设置鼠标悬浮提示
 *
 * @author yc556&claude-opus-5
 * @version 1.0
 */
public class ActionDescriptionTooltip extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
        XmlFileUtils.runActionType(anActionEvent, new XmlFileUtils.Callback() {
            @Override
            public void onModifyPath(List<Pair<String, String>> asBasePathOrExtension, List<XmlEntity> xmlEntities, XmlFile fileDirectoryXml, Project project) {
                final XmlEntity xmlEntity = xmlEntities.get(0);
                String txt = Messages.showInputDialog(project, "请输入鼠标悬浮时显示的提示内容", "设置悬浮提示", Messages.getQuestionIcon(), xmlEntity.getTooltipTitle(), null);
                if (null != txt) {
                    for (XmlEntity x : xmlEntities) {
                        XmlStorage.modify(project, fileDirectoryXml, x.setTooltipTitle(txt));
                    }
                }
            }

            @Override
            public void onCreatePath(List<Pair<String, String>> asBasePathOrExtension, XmlFile fileDirectoryXml, Project project) {
                String txt = Messages.showInputDialog(project, "请输入鼠标悬浮时显示的提示内容", "设置悬浮提示", Messages.getQuestionIcon(), "", null);
                if (null != txt) {
                    for (Pair<String, String> pair : asBasePathOrExtension) {
                        XmlStorage.create(project, fileDirectoryXml, new XmlEntity().setPath(pair.getValue0()).setTooltipTitle(txt));
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
