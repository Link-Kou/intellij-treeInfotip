package com.plugins.infotip;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * A <code>FileDirectory</code> Class
 *
 * @author lk
 * @version 1.0
 * @date 2021/6/7 16:48
 */
public class FileDirectory {

    private static XmlFile xmlFile;
    private static boolean createFile;

    /**
     * 获取到指定的文件
     *
     * @param project 项目
     * @param create  是否创建文件
     */
    public static synchronized XmlFile getFileDirectoryXml(Project project, boolean create) {
        if (project == null) {
            return null;
        }
        //查询相关文件，与目录无关
        PsiFile[] pfs = FilenameIndex.getFilesByName(project, getFileName(), GlobalSearchScope.allScope(project));
        if (pfs.length == 1) {
            //获取一个文件，如果存在多个相同的文件，取查询到第一个
            PsiFile pf = pfs[0];
            if (pf instanceof XmlFile) {
                xmlFile = (XmlFile) pf;
            }
        } else if (pfs.length == 0) {
            if (create) {
                xmlFile = createFileDirectoryXml(project);

            }
        }
        return xmlFile;
    }


    private static String getFileName() {
        return "DirectoryV2.xml";
    }

    /**
     * 创建文件
     *
     * @param project 项目
     */
    private static XmlFile createFileDirectoryXml(Project project) {
        if (project == null) {
            return null;
        }
        LanguageFileType xml = (LanguageFileType) FileTypeManager.getInstance().getStdFileType("XML");
        PsiFile pf = PsiFileFactory.getInstance(project).createFileFromText(getFileName(), xml, "<?xml version=\"1.0\" encoding=\"UTF-8\"?> \r\n <trees/>");
        createFile = true;
        return (XmlFile) pf;
    }

    /**
     * 保存文件
     *
     * @param project 项目
     */
    public static synchronized void saveFileDirectoryXml(Project project, String text, SaveCallBack saveCallBack) {
        if (createFile) {
            createFile = false;
            File f = new File(project.getBasePath() + File.separator + getFileName());
            if (!f.exists()) {
                try {
                    boolean newFile = f.createNewFile();
                    if (newFile) {
                        BufferedWriter writer = null;
                        try {
                            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f, false), StandardCharsets.UTF_8));
                            writer.write(text);
                        } catch (IOException e) {
                            e.printStackTrace();
                        } finally {
                            try {
                                if (writer != null) {
                                    writer.close();
                                    saveCallBack.onSave();
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        //强制更新
        XmlFile fileDirectoryXml = getFileDirectoryXml(project, false);
        XmlParsing.parsing(project, fileDirectoryXml);
    }


    private static boolean isFileName(PsiTreeChangeEvent psiTreeChangeEvent) {
        final PsiFile file = psiTreeChangeEvent.getFile();
        if (null != file) {
            final VirtualFile virtualFile = file.getVirtualFile();
            if (null != virtualFile) {
                return virtualFile.getName().contains(getFileName());
            }
        }
        return false;
    }

    /**
     * 文件监听
     *
     * @param project 项目
     */
    public static void treeChangeListener(Project project) {
        PsiManager.getInstance(project).addPsiTreeChangeListener(
                new PsiTreeChangeListener() {
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
                        if (isFileName(psiTreeChangeEvent)) {
                            XmlFile fileDirectoryXml = getFileDirectoryXml(project, false);
                            XmlParsing.parsing(project, fileDirectoryXml);
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
                            if (getFileName().equals(((XmlFile) child).getName())) {
                                XmlParsing.clear();
                            }
                        }
                    }

                    @Override
                    public void childReplaced(@NotNull PsiTreeChangeEvent psiTreeChangeEvent) {
                    }

                    @Override
                    public void childrenChanged(@NotNull PsiTreeChangeEvent psiTreeChangeEvent) {
                        if (isFileName(psiTreeChangeEvent)) {
                            XmlFile fileDirectoryXml = getFileDirectoryXml(project, false);
                            XmlParsing.parsing(project, fileDirectoryXml);
                        }
                    }

                    @Override
                    public void childMoved(@NotNull PsiTreeChangeEvent psiTreeChangeEvent) {
                    }

                    @Override
                    public void propertyChanged(@NotNull PsiTreeChangeEvent psiTreeChangeEvent) {
                    }
                },
                new Disposable() {
                    @Override
                    public void dispose() {
                    }
                });
    }
}
