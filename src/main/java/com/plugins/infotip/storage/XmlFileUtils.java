package com.plugins.infotip.storage;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.psi.xml.XmlFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE;

/**
 * A <code>XmlUtils</code> Class
 *
 * @author lk
 * @version 1.0
 * <p><b>date: 2023/4/13 12:39</b></p>
 */
public class XmlFileUtils {
    private static final String XMLFileName = "DirectoryV3.xml";

    private final static ConcurrentHashMap<Project, XmlFile> XML_STORAGE_File = new ConcurrentHashMap<Project, XmlFile>();


    public interface Callback {
        /**
         * 修改路径
         *
         * @param asBasePath       绝对路径
         * @param x                对象
         * @param fileDirectoryXml 对象
         * @param project          对象
         * @param extension        对象
         */
        void onModifyPath(String asBasePath, XmlEntity x, XmlFile fileDirectoryXml, Project project, String extension);

        /**
         * 创建路径
         *
         * @param asBasePath       绝对路径
         * @param fileDirectoryXml 对象
         * @param project          对象
         * @param extension        对象
         */
        void onCreatePath(String asBasePath, XmlFile fileDirectoryXml, Project project, String extension);
    }

    /**
     * 获取基础路径
     *
     * @param anActionEvent 对象
     * @param callback      回调
     */
    public static void runActionType(AnActionEvent anActionEvent, Callback callback) {
        final Project project = anActionEvent.getProject();
        if (null == project) {
            return;
        }
        //获取文件、文件夹等对象
        VirtualFile file = VIRTUAL_FILE.getData(anActionEvent.getDataContext());
        if (null != file) {
            XmlFile fileXml = loadXmlFile(project);
            //使用相对路径
            String basePath = project.getPresentableUrl();
            if (null != basePath && basePath.length() > 0) {
                //改为安长度去除
                //此处改进
                String presentableUrl = file.getCanonicalPath();
                if (presentableUrl.length() < basePath.length()) {
                    Messages.showMessageDialog(project, "Unable to get the root path of the file", "Can't Get Path", AllIcons.Actions.Menu_paste);
                    return;
                }
                String asBasePath = presentableUrl.substring(basePath.length(), presentableUrl.length());
                String extension = file.getExtension();
                if (null == fileXml) {
                    final XmlFile xmlFile = createXmlFile(project);
                    callback.onCreatePath(asBasePath, xmlFile, project, extension);
                } else {
                    final List<XmlEntity> xmlEntity = XmlStorage.getXmlEntity(project);
                    if (null == xmlEntity) {
                        XmlStorage.parsing(project, fileXml);
                    }
                    for (XmlEntity x : xmlEntity) {
                        if (asBasePath.equals(x.getPath())) {
                            callback.onModifyPath(asBasePath, x, fileXml, project, extension);
                            break;
                        }
                    }
                }
            }
        } else {
            Messages.showMessageDialog(project, "Unable to get the root path of the project", "Can't Get Path", AllIcons.Actions.Menu_paste);
        }
    }


    /**
     * 创建文件
     *
     * @param project 项目
     * @return XmlFile
     */
    public static XmlFile createXmlFile(Project project) {
        if (project == null) {
            return null;
        }
        LanguageFileType xml = (LanguageFileType) FileTypeManager.getInstance().getStdFileType("XML");
        PsiFile pf = PsiFileFactory.getInstance(project).createFileFromText(XMLFileName, xml, "<?xml version=\"1.0\" encoding=\"UTF-8\"?> \r\n <trees/>");
        return loadSaveFileXml(project, pf.getText());
    }

    /**
     * 加载文件
     *
     * @param project 项目
     * @return XmlFile
     */
    public static XmlFile loadXmlFile(Project project) {
        if (project == null) {
            return null;
        }
        File f = new File(project.getBasePath() + File.separator + XMLFileName);
        VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f);
        if (null != virtualFile) {
            PsiFile file = PsiManager.getInstance(project).findFile(virtualFile);
            if (file instanceof XmlFile) {
                XML_STORAGE_File.put(project, (XmlFile) file);
                return (XmlFile) file;
            }
        }
        return null;
    }

    /**
     * 获取文件
     *
     * @param project 项目
     * @return XmlFile
     */
    public static XmlFile getXmlFile(Project project) {
        return XML_STORAGE_File.get(project);
    }

    /**
     * 获取文件
     *
     * @param project 项目
     * @return XmlFile
     */
    public static XmlFile saveFileXml(Project project) {
        final XmlFile xmlFile = XML_STORAGE_File.get(project);
        return loadSaveFileXml(project, xmlFile.getText());
    }

    /**
     * 是否为指定的文件
     *
     * @param psiTreeChangeEvent 对象
     * @return boolean
     */
    public static boolean isFileName(PsiTreeChangeEvent psiTreeChangeEvent) {
        final PsiFile file = psiTreeChangeEvent.getFile();
        if (null != file) {
            final VirtualFile virtualFile = file.getVirtualFile();
            if (null != virtualFile) {
                return virtualFile.getName().contains(XMLFileName);
            }
        }
        return false;
    }

    /**
     * 是否为指定的文件
     *
     * @param name 名称
     * @return boolean
     */
    public static boolean isFileName(String name) {
        return XMLFileName.equals(name);
    }


    /**
     * 保存文件
     *
     * @param project 项目
     */
    private static synchronized XmlFile loadSaveFileXml(Project project, String text) {
        File f = new File(project.getBasePath() + File.separator + XMLFileName);
        if (!f.exists()) {
            try {
                boolean newFile = f.createNewFile();
                if (newFile) {
                    try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f, false), StandardCharsets.UTF_8))) {
                        writer.write(text);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f);
        if (null != virtualFile) {
            virtualFile.refresh(false, true);
            PsiFile file = PsiManager.getInstance(project).findFile(virtualFile);
            if (file instanceof XmlFile) {
                XML_STORAGE_File.put(project, (XmlFile) file);
                return (XmlFile) file;
            }
        }
        return null;
    }
}
