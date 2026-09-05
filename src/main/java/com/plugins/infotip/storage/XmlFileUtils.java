package com.plugins.infotip.storage;

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
import org.javatuples.Pair;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE_ARRAY;

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

    private static final Map<Object, SaveCallback> callbackList = new ConcurrentHashMap<Object, SaveCallback>();

    public interface Callback {
        /**
         * 修改路径
         *
         * @param asBasePathOrExtension 绝对路径
         * @param x                     对象
         * @param fileDirectoryXml      对象
         * @param project               对象
         */
        void onModifyPath(List<Pair<String, String>> asBasePathOrExtension, List<XmlEntity> x, XmlFile fileDirectoryXml, Project project);

        /**
         * 创建路径
         *
         * @param asBasePathOrExtension 绝对路径
         * @param fileDirectoryXml      对象
         * @param project               对象
         */
        void onCreatePath(List<Pair<String, String>> asBasePathOrExtension, XmlFile fileDirectoryXml, Project project);
    }

    public interface SaveCallback {
        /**
         * 运行
         */
        void run();
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
        //VirtualFile file = VIRTUAL_FILE.getData(anActionEvent.getDataContext());
        final VirtualFile[] files = VIRTUAL_FILE_ARRAY.getData(anActionEvent.getDataContext());
        if (null != files) {
            XmlFile fileXml = loadXmlFile(project);
            //使用相对路径
            String basePath = project.getPresentableUrl();
            if (null != basePath && basePath.length() > 0) {
                //改为安长度去除
                //此处改进
                List<Pair<String, String>> pathInfo = new ArrayList<Pair<String, String>>();
                for (VirtualFile file : files) {
                    String presentableUrl = file.getCanonicalPath();
                    if (presentableUrl.length() < basePath.length()) {
                        Messages.showMessageDialog(project, "无法获取该文件的根路径", "获取路径失败", Messages.getErrorIcon());
                        break;
                    }
                    String asBasePath = presentableUrl.substring(basePath.length(), presentableUrl.length());
                    String extension = file.getExtension();
                    pathInfo.add(Pair.with(asBasePath, extension));
                }
                if (null == fileXml) {
                    final XmlFile xmlFile = createXmlFile(project);
                    callback.onCreatePath(pathInfo, xmlFile, project);
                } else {
                    final List<XmlEntity> xmlEntitys = XmlStorage.getXmlEntity(project);
                    if (null == xmlEntitys) {
                        XmlStorage.parsing(project, fileXml);
                    }
                    final ArrayList<XmlEntity> newXmlEntity = new ArrayList<XmlEntity>();
                    if (null != xmlEntitys) {
                        if (xmlEntitys.size() == 0) {
                            for (Pair<String, String> path : pathInfo) {
                                newXmlEntity.add(new XmlEntity().setPath(path.getValue0()));
                            }
                        } else {
                            for (Pair<String, String> pair : pathInfo) {
                                boolean find = false;
                                for (XmlEntity x : xmlEntitys) {
                                    if (isPathRule(x) && pair.getValue0().equals(x.getPath())) {
                                        find = true;
                                        newXmlEntity.add(x);
                                    }
                                }
                                if (!find) {
                                    newXmlEntity.add(new XmlEntity().setPath(pair.getValue0()));
                                }
                            }
                        }
                        callback.onModifyPath(pathInfo, newXmlEntity, fileXml, project);
                    }
                }
            }
        } else {
            Messages.showMessageDialog(project, "无法获取项目的根路径", "获取路径失败", Messages.getErrorIcon());
        }
    }

    public static void ListenerSave(Object id, SaveCallback callback) {
        callbackList.put(id, callback);
    }

    /**
     * 是否为「路径规则」，即只绑定单个文件或目录的那种。
     * <p>
     * 带 extension 的是「类型规则」，一条会命中一批同扩展名的文件。针对单个节点的菜单
     * 不能顺手把它改掉，否则改一个文件的备注会连带影响整批文件，所以匹配时要排除。
     * </p>
     */
    private static boolean isPathRule(XmlEntity xmlEntity) {
        final String extension = xmlEntity.getExtension();
        return null == extension || extension.trim().isEmpty();
    }

    /**
     * 新项目第一次配置时写出去的 {@code DirectoryV3.xml} 模板
     * <p>
     * {@code <trees>} 下面那段注释是给手改文件的人看的。这个文件躺在项目根目录，用户迟早会点开它，
     * 而 {@code presentableText}、{@code tooltipTitle} 这些参数名单看名字猜不全，更猜不出命中优先级。
     * </p>
     * <p>
     * 注释里有三样东西不能出现，都会被「TreeInfotip XML」窗口的「格式化」误伤：{@code <trees>} 和
     * {@code </trees>}（会被塞进换行）、带空格的 {@code <tree }（会被缩进四格）、以及行尾的
     * {@code >} 紧接下一行开头的 {@code <}（{@code >\s*<} 会被压成一个换行）。另外 XML 注释本身
     * 不允许出现连续两个减号。
     * </p>
     */
    private static final String XML_TEMPLATE = String.join("\r\n",
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
            "<trees>",
            "    <!--",
            "      TreeInfotip 的配置文件：一条 <tree> 就是一条规则，存盘立刻生效，不用重启 IDE。",
            "      平时不用手写，右键项目树上的文件或目录，走「目录备注」菜单加就行。",
            "",
            "      参数全是可选的，按需要写几个：",
            "        path             相对项目根目录的路径，以 / 开头；只写 / 表示整个项目",
            "        extension        扩展名，不带点，只作用于文件；和 path 一起写表示该目录连子目录下的这类文件",
            "        title            备注文字，灰色跟在节点名后面",
            "        presentableText  覆盖节点显示的名字",
            "        tooltipTitle     鼠标悬浮时的提示，可以写多行",
            "        icon             换图标，填 AllIcons 里的字段路径，例如 Nodes.Folder",
            "        textColor        文字颜色，十进制 r,g,b，例如 255,0,0",
            "        backgroundColor  背景色，写法同上",
            "        strikethrough    填 true 给节点加删除线",
            "",
            "      命中优先级：path 全等的最高，其次 path 加 extension（path 更长的赢），",
            "      最后是只写 extension 的全项目规则。同优先级时写在前面的那条赢，所以侧边栏",
            "      「目录备注」里的「置顶」是真的把标签挪到文件最前面。",
            "    -->",
            "</trees>");

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
        PsiFile pf = PsiFileFactory.getInstance(project).createFileFromText(XMLFileName, xml, XML_TEMPLATE);
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
            //virtualFile.refresh(false, true);
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
                for (Map.Entry<Object, SaveCallback> objectSaveCallbackEntry : callbackList.entrySet()) {
                    objectSaveCallbackEntry.getValue().run();
                }
                return (XmlFile) file;
            }
        }
        return null;
    }
}
