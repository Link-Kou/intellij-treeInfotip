package com.plugins.infotip.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

/**
 * 右键菜单
 *
 * @author lk
 * @version 1.0
 * 2021/6/7 14:13
 */
public class ActionDescriptionClearAll extends AnAction {


    @Override
    public void actionPerformed(@NotNull AnActionEvent anActionEvent) {

        /*final Project project = anActionEvent.getProject();
        if (project != null) {
            final ProjectViewImpl instance = (ProjectViewImpl) ProjectView.getInstance(project);
            final VirtualFile file = VfsUtil.findFile(new File(project.getBasePath() + "/src/main/java/com/linkkou/mybatis").toPath(), false);
            if (null != file) {
                final PsiManager instance1 = PsiManager.getInstance(project);
                if (file.isDirectory()) {
                    final PsiDirectory directory = instance1.findDirectory(file);
                    instance.selectPsiElement(directory, true);
                } else {
                    final PsiFile file1 = instance1.findFile(file);
                    instance.selectPsiElement(file1, true);
                }
            }
        }*/
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
