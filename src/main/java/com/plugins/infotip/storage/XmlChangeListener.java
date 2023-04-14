package com.plugins.infotip.storage;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;

/**
 * A <code>XmlChangeListener</code> Class
 *
 * @author lk
 * @version 1.0
 * <p><b>date: 2023/4/13 15:11</b></p>
 */
public class XmlChangeListener {

    /**
     * 文件监听
     *
     * @param project 项目
     */
    public static void run(Project project) {
        PsiManager.getInstance(project).addPsiTreeChangeListener(new PsiTreeChangeListener() {
            @Override
            public void beforeChildAddition(@NotNull PsiTreeChangeEvent psiTreeChangeEvent) {
            }

            @Override
            public void beforeChildRemoval(@NotNull PsiTreeChangeEvent psiTreeChangeEvent) {
            }

            @Override
            public void beforeChildReplacement(@NotNull PsiTreeChangeEvent psiTreeChangeEvent) {
            }

            @Override
            public void beforeChildMovement(@NotNull PsiTreeChangeEvent psiTreeChangeEvent) {
            }

            //更新前
            @Override
            public void beforeChildrenChange(@NotNull PsiTreeChangeEvent psiTreeChangeEvent) {
                if (XmlFileUtils.isFileName(psiTreeChangeEvent)) {
                    final PsiFile file = psiTreeChangeEvent.getFile();
                    if (file instanceof XmlFile) {
                        XmlFileUtils.loadXmlFile(project);
                        XmlStorage.parsing(project, (XmlFile) file);
                    }
                }
            }

            @Override
            public void beforePropertyChange(@NotNull PsiTreeChangeEvent psiTreeChangeEvent) {
            }

            @Override
            public void childAdded(@NotNull PsiTreeChangeEvent psiTreeChangeEvent) {
            }

            @Override
            public void childRemoved(@NotNull PsiTreeChangeEvent psiTreeChangeEvent) {
                PsiElement child = psiTreeChangeEvent.getChild();
                if (child instanceof XmlFile) {
                    if (XmlFileUtils.isFileName(((XmlFile) child).getName())) {
                        XmlStorage.clear(project);
                    }
                }
            }

            @Override
            public void childReplaced(@NotNull PsiTreeChangeEvent psiTreeChangeEvent) {
            }

            @Override
            public void childrenChanged(@NotNull PsiTreeChangeEvent psiTreeChangeEvent) {
                if (XmlFileUtils.isFileName(psiTreeChangeEvent)) {
                    final PsiFile file = psiTreeChangeEvent.getFile();
                    if (file instanceof XmlFile) {
                        XmlStorage.parsing(project, (XmlFile) file);
                    }
                }
            }

            @Override
            public void childMoved(@NotNull PsiTreeChangeEvent psiTreeChangeEvent) {
            }

            @Override
            public void propertyChanged(@NotNull PsiTreeChangeEvent psiTreeChangeEvent) {
            }
        }, new Disposable() {
            @Override
            public void dispose() {
            }
        });
    }

}
