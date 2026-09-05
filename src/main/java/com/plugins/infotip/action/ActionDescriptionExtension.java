package com.plugins.infotip.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlFile;
import com.plugins.infotip.gui.view.SelectColorIconsView;
import com.plugins.infotip.storage.XmlEntity;
import com.plugins.infotip.storage.XmlFileUtils;
import com.plugins.infotip.storage.XmlStorage;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;

import static com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE_ARRAY;

/**
 * A <code>ActionDescriptionExtension</code> Class
 * 右键菜单：按扩展名批量设置
 * <p>
 * 其他菜单写的都是「路径规则」，一条只管一个文件。这里写的是「类型规则」，一条管一批同扩展名的文件：
 * <ul>
 *     <li>仅此目录 —— {@code <tree path="/src/main/java" extension="java" title="源码"/>}，
 *     只对该目录（含子目录）下的 .java 生效；</li>
 *     <li>整个项目 —— {@code <tree extension="java" title="源码"/>}，不写 path，全项目的 .java 都生效。</li>
 * </ul>
 * 单个文件自己的路径规则优先级更高，会盖过这里的类型规则。
 * </p>
 *
 * @author yc556&claude-opus-5
 * @version 1.0
 */
public class ActionDescriptionExtension extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
        final Project project = anActionEvent.getProject();
        if (null == project) {
            return;
        }
        final VirtualFile[] files = VIRTUAL_FILE_ARRAY.getData(anActionEvent.getDataContext());
        if (null == files || files.length == 0) {
            Messages.showMessageDialog(project, "没有选中任何文件或目录", "按扩展名设置", Messages.getErrorIcon());
            return;
        }
        final String basePath = project.getPresentableUrl();
        if (null == basePath || basePath.isEmpty()) {
            Messages.showMessageDialog(project, "无法获取项目的根路径", "获取路径失败", Messages.getErrorIcon());
            return;
        }
        //选中文件时取它所在的目录，选中目录时就用目录自己
        final VirtualFile selected = files[0];
        final VirtualFile directory = selected.isDirectory() ? selected : selected.getParent();
        final String directoryPath = null == directory ? null : relativePath(directory, basePath);
        if (null == directoryPath) {
            Messages.showMessageDialog(project, "无法获取该目录的相对路径", "获取路径失败", Messages.getErrorIcon());
            return;
        }

        final String input = Messages.showInputDialog(project, "对哪种扩展名生效？不要带点，例如 java", "按扩展名设置",
                Messages.getQuestionIcon(), selected.isDirectory() ? "" : trimToEmpty(selected.getExtension()), null);
        if (null == input || input.trim().isEmpty()) {
            return;
        }
        final String extension = input.trim();

        //根目录本身就等于整个项目，没必要再问一遍
        final String rulePath;
        if (directoryPath.isEmpty()) {
            rulePath = null;
        } else {
            final int scope = Messages.showDialog(project, "这条规则的生效范围？", "按扩展名设置",
                    new String[]{"仅 " + directoryPath + " 下", "整个项目", "取消"}, 0, Messages.getQuestionIcon());
            if (scope != 0 && scope != 1) {
                return;
            }
            rulePath = scope == 0 ? directoryPath : null;
        }

        XmlFile xmlFile = XmlFileUtils.loadXmlFile(project);
        if (null == xmlFile) {
            xmlFile = XmlFileUtils.createXmlFile(project);
        }
        if (null == xmlFile) {
            Messages.showMessageDialog(project, "无法创建配置文件", "按扩展名设置", Messages.getErrorIcon());
            return;
        }

        //同范围同扩展名的规则只应存在一条，已存在就改它，避免堆出重复规则
        if (null == XmlStorage.getXmlEntity(project)) {
            XmlStorage.parsing(project, xmlFile);
        }
        final XmlEntity exists = findRule(project, rulePath, extension);
        if (null != exists) {
            //按节点清除的菜单碰不到类型规则，所以删除入口只能放在这里
            final int action = Messages.showDialog(project,
                    "已经有一条针对 *." + extension + " 的规则了，要怎么处理？", "按扩展名设置",
                    new String[]{"修改", "删除", "取消"}, 0, Messages.getQuestionIcon());
            if (action == 1) {
                XmlStorage.remove(xmlFile, project, exists);
                return;
            }
            if (action != 0) {
                return;
            }
        }
        final String title = Messages.showInputDialog(project, "请输入备注内容，留空则不显示备注", "按扩展名设置",
                Messages.getQuestionIcon(), null == exists ? "" : trimToEmpty(exists.getTitle()), null);
        if (null == title) {
            return;
        }

        final SelectColorIconsView dialog = new SelectColorIconsView(project);
        dialog.pack();
        dialog.setTitle("选择图标或颜色（." + extension + "）");
        dialog.setPreferredSize(new Dimension(380, 200));
        dialog.setSize(380, 200);
        dialog.setLocationRelativeTo(null);
        dialog.setResizable(false);
        dialog.setModal(true);
        if (null != exists) {
            dialog.setIcons(exists.getIcon());
            dialog.setTextColor(exists.getTextColor());
            dialog.setBackgroundColor(exists.getBackgroundColor());
        }
        dialog.setVisible(true);

        final XmlEntity rule = null == exists ? new XmlEntity().setPath(rulePath).setExtension(extension) : exists;
        rule.setTitle(title).setIcon(dialog.getIcons()).setTextColor(dialog.getTextColor()).setBackgroundColor(dialog.getBackgroundColor());
        if (null == exists) {
            XmlStorage.create(project, xmlFile, rule);
        } else {
            XmlStorage.modify(project, xmlFile, rule);
        }
    }

    /**
     * 找出已存在的同范围同扩展名规则
     *
     * @param rulePath  限定目录，null 表示整个项目
     * @param extension 扩展名
     * @return 没有则返回 null
     */
    private static XmlEntity findRule(Project project, String rulePath, String extension) {
        final List<XmlEntity> entities = XmlStorage.getXmlEntity(project);
        if (null == entities) {
            return null;
        }
        for (XmlEntity entity : entities) {
            if (null == entity) {
                continue;
            }
            if (extension.equalsIgnoreCase(trimToEmpty(entity.getExtension()))
                    && trimToEmpty(rulePath).equals(trimToEmpty(entity.getPath()))) {
                return entity;
            }
        }
        return null;
    }

    private static String relativePath(VirtualFile file, String basePath) {
        final String canonicalPath = file.getCanonicalPath();
        if (null == canonicalPath || canonicalPath.length() < basePath.length()) {
            return null;
        }
        return canonicalPath.substring(basePath.length());
    }

    private static String trimToEmpty(String value) {
        return null == value ? "" : value.trim();
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
