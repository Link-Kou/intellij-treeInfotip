package com.plugins.infotip.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlFile;
import com.plugins.infotip.storage.XmlEntity;
import com.plugins.infotip.storage.XmlFileUtils;
import com.plugins.infotip.storage.XmlStorage;
import org.javatuples.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 右键菜单：给节点文本添加/取消删除线
 *
 * @author lk
 * @version 1.0
 */
public class ActionDescriptionStrikethrough extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
        XmlFileUtils.runActionType(anActionEvent, new XmlFileUtils.Callback() {
            @Override
            public void onModifyPath(List<Pair<String, String>> asBasePathOrExtension, List<XmlEntity> xmlEntities, XmlFile fileDirectoryXml, Project project) {
                //以第一个选中节点的当前状态取反，作为本次批量操作的目标状态，
                //这样多选时行为一致，不会出现有的加上有的取消。
                final boolean enable = !xmlEntities.get(0).isStrikethroughEnabled();
                for (XmlEntity x : xmlEntities) {
                    XmlStorage.modify(project, fileDirectoryXml, x.setStrikethrough(enable ? "true" : null));
                }
            }

            @Override
            public void onCreatePath(List<Pair<String, String>> asBasePathOrExtension, XmlFile fileDirectoryXml, Project project) {
                //尚无任何配置时，直接为选中节点建立带删除线的配置
                for (Pair<String, String> pair : asBasePathOrExtension) {
                    XmlStorage.create(project, fileDirectoryXml,
                            new XmlEntity().setPath(pair.getValue0()).setStrikethrough("true"));
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
