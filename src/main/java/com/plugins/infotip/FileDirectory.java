package com.plugins.infotip;

import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import com.plugins.infotip.ui.Icons;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE;
import static com.plugins.infotip.FileIcons.getAllIcons;

/**
 * A <code>FileDirectory</code> Class
 *
 * @author lk
 * @version 1.0
 * @date 2021/6/7 16:48
 */
public class FileDirectory {

    private static XmlFile xmlFile;

    public interface Callback {
        /**
         * 修改路径
         *
         * @param asbbasePath      绝对路径
         * @param x                对象
         * @param fileDirectoryXml 对象
         * @param project          对象
         * @param extension        对象
         */
        void onModifyPath(String asbbasePath, XmlEntity x, XmlFile fileDirectoryXml, Project project, String extension);

        /**
         * 创建路径
         *
         * @param asbbasePath      绝对路径
         * @param fileDirectoryXml 对象
         * @param project          对象
         * @param extension        对象
         */
        void onCreatePath(String asbbasePath, XmlFile fileDirectoryXml, Project project, String extension);
    }

    /**
     * 获取基础路径
     *
     * @param anActionEvent 对象
     * @param callback      回调
     */
    public static void getBasePath(AnActionEvent anActionEvent, Callback callback) {
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
                String presentableUrl = file.getCanonicalPath();
                if (presentableUrl.length() < basePath.length()) {
                    Messages.showMessageDialog(project, "Unable to get the root path of the file", "Can't Get Path", AllIcons.Actions.Menu_paste);
                    return;
                }
                String asbbasePath = presentableUrl.substring(basePath.length(), presentableUrl.length());
                String extension = file.getExtension();
                boolean notfind = false;
                for (XmlEntity x : refreshXml) {
                    if (presentableUrl.equals(x.getPath())) {
                        callback.onModifyPath(asbbasePath, x, fileDirectoryXml, project, extension);
                        notfind = true;
                        break;
                    }
                }
                if (!notfind) {
                    callback.onCreatePath(asbbasePath, fileDirectoryXml, project, extension);
                }
            }
        } else {
            Messages.showMessageDialog(project, "Unable to get the root path of the project", "Can't Get Path", AllIcons.Actions.Menu_paste);
        }
    }

    /**
     * 获取到指定的文件
     *
     * @param project 项目
     * @param create  是否创建文件
     */
    public static synchronized XmlFile getFileDirectoryXml(final Project project, boolean create) {
        if (project == null) {
            return null;
        }
        //查询相关文件，与目录无关
        try {
            //git支持不友好的解决
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
        } catch (Exception e) {
            e.printStackTrace();
        }
        return xmlFile;
    }

    /**
     * 获取文件名称
     *
     * @return String
     */
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
        saveFileDirectoryXml(project, pf.getText());
        return xmlFile;
    }

    /**
     * 保存文件
     *
     * @param project 项目
     */
    public static synchronized void saveFileDirectoryXml(Project project, String text) {
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
                        return;
                    } finally {
                        try {
                            if (writer != null) {
                                writer.close();
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }
        VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f);
        if (null != virtualFile) {
            virtualFile.refresh(false, true);
            PsiFile file = PsiManager.getInstance(project).findFile(virtualFile);
            if (file instanceof XmlFile) {
                xmlFile = (XmlFile) file;
                XmlParsing.parsing(project, xmlFile);
            }
        }

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
                        final PsiElement parent = psiTreeChangeEvent.getParent();
                        if (null != parent) {
                            final Project project1 = parent.getProject();
                            if (isFileName(psiTreeChangeEvent)) {
                                XmlFile fileDirectoryXml = getFileDirectoryXml(project1, false);
                                XmlParsing.parsing(project1, fileDirectoryXml);
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
                        final PsiElement parent = psiTreeChangeEvent.getParent();
                        if (null != parent) {
                            final Project project1 = parent.getProject();
                            if (isFileName(psiTreeChangeEvent)) {
                                XmlFile fileDirectoryXml = getFileDirectoryXml(project1, false);
                                XmlParsing.parsing(project1, fileDirectoryXml);
                            }
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

    /**
     * 是否为指定的文件
     *
     * @param psiTreeChangeEvent 对象
     * @return boolean
     */
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
     * 设置节点备注
     *
     * @param abstractTreeNode 对象
     */
    public static void setLocationString(AbstractTreeNode<?> abstractTreeNode) {
        if (null != abstractTreeNode) {
            Method[] methods1 = abstractTreeNode.getClass().getMethods();
            Object value = abstractTreeNode.getValue();
            if (null != value) {
                Method[] methods2 = value.getClass().getMethods();
                VirtualFile virtualFile2 = getVirtualFile(methods2, value);
                if (null != virtualFile2) {
                    setXmlToLocationString(virtualFile2, abstractTreeNode);
                    return;
                }
            }
            VirtualFile virtualFile1 = getVirtualFile(methods1, abstractTreeNode);
            if (null != virtualFile1) {
                setXmlToLocationString(virtualFile1, abstractTreeNode);
            }
        }
    }

    /**
     * 设置节点备注
     *
     * @param node 对象
     * @param data 对象
     */
    public static void setLocationString(ProjectViewNode node, PresentationData data) {
        if (null != node) {
            Method[] methods1 = node.getClass().getMethods();
            Object value = node.getValue();
            if (null != value) {
                Method[] methods2 = value.getClass().getMethods();
                VirtualFile virtualFile2 = getVirtualFile(methods2, value);
                if (null != virtualFile2) {
                    setXmlToLocationString(virtualFile2, data);
                    return;
                }
            }
            VirtualFile virtualFile1 = getVirtualFile(methods1, node);
            if (null != virtualFile1) {
                setXmlToLocationString(virtualFile1, data);
            }
        }
    }

    /**
     * 获取到 VirtualFile
     *
     * @param methods 方法
     * @param o       对象
     * @return VirtualFile
     */
    private static VirtualFile getVirtualFile(Method[] methods, Object o) {
        for (Method method : methods) {
            if ("getVirtualFile".equals(method.getName())) {
                method.setAccessible(true);
                try {
                    Object invoke = method.invoke(o);
                    if (invoke instanceof VirtualFile) {
                        return (VirtualFile) invoke;
                    }
                } catch (IllegalAccessException | InvocationTargetException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    /**
     * 设置备注
     *
     * @param virtualFile      对象
     * @param abstractTreeNode 对象
     */
    private static void setXmlToLocationString(VirtualFile virtualFile, AbstractTreeNode<?> abstractTreeNode) {
        XmlEntity matchPath = getMatchPath(virtualFile);
        if (null != matchPath) {
            //设置备注
            abstractTreeNode.getPresentation().setLocationString(matchPath.getTitle());
        }
    }

    /**
     * 设置备注
     *
     * @param virtualFile 对象
     * @param data        对象
     */
    private static void setXmlToLocationString(VirtualFile virtualFile, PresentationData data) {
        XmlEntity matchPath = getMatchPath(virtualFile);
        if (null != matchPath) {
            //设置备注
            data.setLocationString(matchPath.getTitle());
            for (Icons allIcon : getAllIcons()) {
                if (allIcon.getName().equals(matchPath.getIcon())) {
                    data.setIcon(allIcon.getIcon());
                    return;
                }
            }
        }
    }

    /**
     * 匹配路径
     *
     * @param virtualFile 文件对象
     * @return boolean
     */
    private static XmlEntity getMatchPath(VirtualFile virtualFile) {
        List<XmlEntity> xml = XmlParsing.getXml();
        for (XmlEntity listTreeInfo : xml) {
            if (listTreeInfo != null) {
                String canonicalPath = virtualFile.getCanonicalPath();
                if (null != canonicalPath) {
                    if (canonicalPath.equals(listTreeInfo.getPath())) {
                        return listTreeInfo;
                    }
                }
            }
        }
        return null;
    }
}
