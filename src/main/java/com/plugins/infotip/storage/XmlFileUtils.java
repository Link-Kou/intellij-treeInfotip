package com.plugins.infotip.storage;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
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
    /**
     * 现在用的配置文件名。
     *
     * <p>
     * 7.0.0 起是 {@code DirectoryV7.xml}，内容改成了嵌套结构、属性名也全换了，老版本插件读不了，
     * 所以换个文件名让新旧各读各的。<b>老文件不改名也不删</b>：启动时发现只有老的，就按它的内容
     * 另外生成一份 V7，原件原样留着，见 {@link #migrateIfNeeded}。
     * </p>
     */
    private static final String XMLFileName = "DirectoryV7.xml";

    /**
     * 6.x 的文件名，只在迁移时读一次
     */
    private static final String V6_FILE_NAME = "DirectoryV6.xml";

    /**
     * 5.x 及之前的文件名，只在迁移时读一次，V6 不在时才轮到它
     */
    private static final String LEGACY_XML_FILE_NAME = "DirectoryV3.xml";

    /**
     * 现在用的文件名，给界面文案用
     */
    public static String currentFileName() {
        return XMLFileName;
    }

    /**
     * 通知分组 id，必须和 {@code plugin.xml} 里注册的那个一致
     */
    private static final String NOTIFICATION_GROUP = "TreeInfoTip Notes";

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
                                    if (pair.getValue0().equals(x.getPath())) {
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
     * {@code <trees>} 之前的部分
     */
    private static final String XML_HEADER = String.join("\r\n",
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
      "<!-- JetBrains 安装插件【TreeInfoTip Notes / 目录树备注】 安装后就能看到目录后面的备注信息 -->",
      "");

    /**
     * {@code </trees>} 之后那段参数说明
     * <p>
     * 这段注释是给手改文件的人看的。文件躺在项目根目录，用户迟早会点开它，而 {@code label}、
     * {@code tooltip} 这些参数名单看名字猜不全，更猜不出嵌套规则和命中优先级。
     * </p>
     * <p>
     * <b>XML 注释里不能出现连续两个减号</b>，改这段时注意。
     * </p>
     */
    private static final String XML_FOOTER = "\r\n" + String.join("\r\n",
      "<!--",
      "  TreeInfoTip Notes 的配置文件：一个 node 就是一条规则，存盘立刻生效，不用重启 IDE。",
      "  平时不用手写，右键项目树上的文件或目录，走「目录备注」菜单加就行。",
      "",
      "  节点是嵌套的，每层的 path 只写相对上一层的那一截，完整路径由各层拼起来：",
      "",
      "    <trees>",
      "      <node path=\"src/main/java\">",
      "        <node path=\"Foo.java\" note=\"入口\"/>",
      "        <node path=\"App.java\" color=\"255,0,0\"/>",
      "      </node>",
      "    </trees>",
      "",
      "  只为了分层而存在、自己什么都没配的节点不算规则，删光底下的规则时会跟着消失。",
      "",
      "  参数全是可选的，按需要写几个：",
      "    path     相对上一层的路径；写在最外层就是相对项目根目录",
      "    note     备注文字，灰色跟在节点名后面",
      "    label    覆盖节点显示的名字",
      "    tooltip  鼠标悬浮时的提示，要换行写 &#10;",
      "    icon     换图标，填 AllIcons 里的字段路径，例如 Nodes.Folder",
      "    color    文字颜色，十进制 r,g,b，例如 255,0,0",
      "    bg       背景色，写法同上",
      "    strike   填 true 给节点加删除线",
      "",
      "  一个 node 只对完整路径全等的那一个文件或目录生效。同一路径写了多条时写在前面的赢，",
      "  所以侧边栏「目录备注」里的「置顶」是真的把标签挪到同层兄弟的最前面。",
      "-->");

    /**
     * 新项目第一次配置时写出去的 {@code DirectoryV7.xml} 模板
     */
    private static final String XML_TEMPLATE = XML_HEADER + "<trees>\r\n</trees>" + XML_FOOTER;

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
        //新建一律用 V7，老名字只在迁移时读一次
        return loadSaveFileXml(project, pf.getText(), XMLFileName);
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
        final VirtualFile virtualFile = findConfigFile(project);
        if (null == virtualFile) {
            return null;
        }
        final XmlFile xmlFile = findXmlPsi(project, virtualFile);
        if (null != xmlFile) {
            XML_STORAGE_File.put(project, xmlFile);
        }
        return xmlFile;
    }

    /**
     * 找当前在用的配置文件，也就是 V7
     *
     * <p>
     * <b>只认 V7</b>。老文件是迁移的输入，不是运行时的配置源——真让它当配置源，
     * 用户在项目树上改一条备注就得写回老格式，V7 的嵌套结构反而落不了地。
     * </p>
     *
     * @param project 项目
     * @return 找不到返回 null
     */
    public static VirtualFile findConfigFile(Project project) {
        return findByName(project, XMLFileName);
    }

    /**
     * 找可以迁移的老配置：V6 优先，没有就退回 V3
     *
     * @return 两个都没有时返回 null
     */
    private static VirtualFile findLegacyFile(Project project) {
        final VirtualFile v6 = findByName(project, V6_FILE_NAME);
        return null != v6 ? v6 : findByName(project, LEGACY_XML_FILE_NAME);
    }

    private static VirtualFile findByName(Project project, String fileName) {
        if (project == null || project.getBasePath() == null) {
            return null;
        }
        return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(
                new File(project.getBasePath() + File.separator + fileName));
    }

    /**
     * 开项目时把老配置转成 V7
     *
     * <p>
     * 判据就一条：<b>已经有 V7 就什么都不做</b>。没有才去找 V6 / V3，找到就按它的内容生成一份
     * {@code DirectoryV7.xml}。<b>老文件原样留着</b>，不改名也不删：它在用户的版本库里，
     * 别的同事可能还在用旧版插件，删了对方就全丢了；留着的代价只是多一个文件。
     * </p>
     * <p>
     * 也因此这件事只会发生一次——V7 一旦生成，下次开项目就直接走上面那条判据回来了。
     * 用户要重新迁移，把 V7 删掉再开一次就行。
     * </p>
     * <p>
     * 它要开写操作（{@code loadSaveFileXml} 落盘），别在写操作或 PSI 事件回调里调。
     * </p>
     *
     * @param project 项目
     * @return 真的生成了 V7 返回 true
     */
    public static boolean migrateIfNeeded(Project project) {
        if (null != findConfigFile(project)) {
            return false;
        }
        final VirtualFile legacy = findLegacyFile(project);
        if (null == legacy) {
            return false;
        }
        final XmlFile legacyPsi = findXmlPsi(project, legacy);
        if (null == legacyPsi) {
            return false;
        }
        //读老文件要读锁：PSI 的属性访问在新版平台上不再由 EDT 隐式持有
        final List<XmlEntity>[] holder = new List[1];
        ApplicationManager.getApplication().runReadAction(() -> {
            holder[0] = LegacyReader.read(legacyPsi);
        });
        final List<XmlEntity> entities = null == holder[0] ? new ArrayList<>() : holder[0];
        final String text = V7Migrator.buildDocument(entities, XML_HEADER, XML_FOOTER);
        if (null == loadSaveFileXml(project, text, XMLFileName)) {
            return false;
        }
        notifyMigrated(project, legacy.getName(), entities.size());
        return true;
    }

    /**
     * 迁移之后发一条通知
     *
     * <p>
     * 用户项目根目录里凭空多出一个会进版本库的文件，不能一声不响：他下次提交会看到一个新增，
     * 得知道是插件干的、为什么干的、老的那份还在不在。
     * </p>
     */
    private static void notifyMigrated(Project project, String from, int count) {
        final NotificationGroupManager manager = NotificationGroupManager.getInstance();
        if (!manager.isGroupRegistered(NOTIFICATION_GROUP)) {
            return;
        }
        manager.getNotificationGroup(NOTIFICATION_GROUP)
                .createNotification("配置已升级到 V7",
                        "已按 " + from + " 里的 " + count + " 条规则生成 " + XMLFileName
                                + "，改成了嵌套结构、属性名也换短了。"
                                + from + " 原样留着没动，旧版插件仍然读它；确认没问题后可以自行删除。",
                        NotificationType.INFORMATION)
                .notify(project);
    }

    /**
     * 把 {@link VirtualFile} 转成 PSI
     *
     * <p>
     * 读 PSI 要读锁，而新版平台的 EDT 不再隐式持有它，从 Swing 监听器调过来就会抛
     * {@code Read access is allowed from inside read-action only}。所以在这里自己包一层读操作，
     * 每个调用点就不用各自记得包了。
     * </p>
     * <p>
     * VFS 刷新必须留在这个方法<b>外面</b>：同步刷新自己要开写操作，套进读操作里是换一个错。
     * </p>
     *
     * @param project     项目
     * @param virtualFile 文件
     * @return XmlFile，不是 XML 就返回 null
     */
    private static XmlFile findXmlPsi(Project project, VirtualFile virtualFile) {
        final PsiFile[] holder = new PsiFile[1];
        ApplicationManager.getApplication().runReadAction(() -> {
            holder[0] = PsiManager.getInstance(project).findFile(virtualFile);
        });
        return holder[0] instanceof XmlFile ? (XmlFile) holder[0] : null;
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
     * <p>
     * 落盘用的是缓存里那个 PsiFile 自己的文件名，虽然 7.0.0 起缓存里一定是 V7，还是保留这个写法：
     * 哪天再来一次大版本迁移，硬写文件名会凭空造出第二份配置，两边内容从此各走各的。
     * </p>
     *
     * @param project 项目
     * @return XmlFile
     */
    public static XmlFile saveFileXml(Project project) {
        final XmlFile xmlFile = XML_STORAGE_File.get(project);
        if (null == xmlFile) {
            return null;
        }
        final VirtualFile virtualFile = xmlFile.getVirtualFile();
        final String fileName = null == virtualFile ? XMLFileName : virtualFile.getName();
        return loadSaveFileXml(project, xmlFile.getText(), fileName);
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
                return isFileName(virtualFile.getName());
            }
        }
        return false;
    }

    /**
     * 是否为指定的文件
     *
     * <p>
     * <b>只认 V7</b>。老文件在迁移那一刻读完就退休了，之后用户再改它也不该触发重新解析——
     * 那会把内存里的 V7 配置覆盖成老文件的内容，而下一次存盘又写回 V7，两份就对不上了。
     * </p>
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
     * @param project  项目
     * @param text     文件不存在时写进去的内容
     * @param fileName 要落盘的文件名，见 {@link #saveFileXml} 和 {@link #createXmlFile} 的区别
     */
    private static synchronized XmlFile loadSaveFileXml(Project project, String text, String fileName) {
        File f = new File(project.getBasePath() + File.separator + fileName);
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
            final XmlFile xmlFile = findXmlPsi(project, virtualFile);
            if (null != xmlFile) {
                XML_STORAGE_File.put(project, xmlFile);
                for (Map.Entry<Object, SaveCallback> objectSaveCallbackEntry : callbackList.entrySet()) {
                    objectSaveCallbackEntry.getValue().run();
                }
                return xmlFile;
            }
        }
        return null;
    }
}
