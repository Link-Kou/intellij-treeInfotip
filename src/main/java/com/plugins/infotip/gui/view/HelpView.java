package com.plugins.infotip.gui.view;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.HTMLEditorKitBuilder;
import com.intellij.util.ui.JBUI;
import com.plugins.infotip.storage.XmlFileUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.ScrollPaneConstants;

/**
 * A <code>HelpView</code> Class
 * <p>
 * 「说明」tab：一页 HTML，讲清楚菜单怎么用、{@code DirectoryV3.xml} 有哪些参数、命中优先级怎么算。
 * </p>
 * <p>
 * 做成 tab 而不是弹窗：参数表要对着配置文件看，弹窗一关就没了，而侧边栏可以一直开着。
 * </p>
 * <p>
 * 用 {@link JEditorPane} 而不是拼一堆 Swing 控件，是为了拿现成的 HTML 排版（标题、列表、等宽字体）。
 * 三个必要的设置：
 * </p>
 * <ul>
 *   <li>{@code HTMLEditorKitBuilder} 建的 kit 才带平台的默认样式表（字体、颜色跟着主题走）。
 *   {@code withWordWrapViewFactory()} 负责长行折行。<b>建完不要再调 {@code setContentType}</b>，
 *   那会把 kit 换回 Swing 自带的。</li>
 *   <li>覆盖 {@link #getScrollableTracksViewportWidth()} 返回 {@code true}，正文才会按侧边栏的
 *   实际宽度重排，否则它按内容的理想宽度铺开，只能靠横向滚动条看。</li>
 *   <li>{@code setOpaque(false)}：{@code JEditorPane} 的背景色来自 LaF 的 {@code EditorPane.background}，
 *   透明之后露出 viewport 的底色，浅色和深色主题都不会突然出现一块白。</li>
 * </ul>
 *
 * @author yc556&claude-opus-5
 * @version 1.0
 */
public class HelpView extends JEditorPane {

    /**
     * 工具栏的 {@code place}，只用于 action 事件溯源，不是全局注册的 id
     */
    private static final String PLACE_TOOLBAR = "TreeInfotipHelpToolbar";

    private final Project project;

    private HelpView(@NotNull Project project) {
        this.project = project;
        setEditable(false);
        setOpaque(false);
        setEditorKit(new HTMLEditorKitBuilder().withWordWrapViewFactory().build());
        setText(html());
        //setText 之后光标停在末尾的话，滚动面板会直接滚到最下面
        setCaretPosition(0);
        setBorder(JBUI.Borders.empty(8, 10));
    }

    /**
     * 建出「说明」这个 tab 的整块内容
     *
     * @param project 当前项目
     * @return 直接塞给 {@code ContentFactory#createContent} 的组件
     */
    public static JComponent createPanel(@NotNull Project project) {
        final HelpView view = new HelpView(project);
        final SimpleToolWindowPanel panel = new SimpleToolWindowPanel(true, true);
        panel.setToolbar(view.createToolbar());
        final JBScrollPane scroll = new JBScrollPane(view);
        //正文已经按宽度折行了，横向滚动条只会在 <pre> 例子超宽时冒出来，占一行还挡字
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        panel.setContent(scroll);
        return panel;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        //必须跟着 viewport 的宽度走，见类注释
        return true;
    }

    private JComponent createToolbar() {
        final DefaultActionGroup group = new DefaultActionGroup();
        group.add(new OpenXmlAction());
        final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(PLACE_TOOLBAR, group, true);
        //不设 targetComponent 平台会警告，action 的 update 也拿不到正确的 DataContext
        toolbar.setTargetComponent(this);
        return toolbar.getComponent();
    }

    /**
     * 工具栏上的「打开 DirectoryV3.xml」，省得用户自己去项目根目录里翻
     * <p>
     * 文件不存在时只提示，<b>不顺手建一个</b>：空配置文件对用户没用，而右键菜单加第一条备注时
     * 会自动建（{@link XmlFileUtils#createXmlFile}），带着参数说明的注释也是那时写进去的。
     * </p>
     */
    private class OpenXmlAction extends AnAction {

        OpenXmlAction() {
            super("打开 DirectoryV3.xml", "打开项目根目录下的配置文件", AllIcons.FileTypes.Xml);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            final XmlFile xmlFile = XmlFileUtils.getXmlFile(project);
            if (null == xmlFile) {
                Messages.showInfoMessage(project, "这个项目还没有 DirectoryV3.xml。\n"
                        + "在项目树上右键任意文件或目录 → 目录备注 → 添加文字备注，加第一条备注时会自动建出来。", "打开配置文件");
                return;
            }
            //取 PSI 的 VirtualFile 要在读操作里，打开编辑器要在读操作外面
            final VirtualFile[] file = {null};
            ApplicationManager.getApplication().runReadAction(() -> {
                file[0] = xmlFile.getVirtualFile();
            });
            if (null == file[0]) {
                Messages.showInfoMessage(project, "配置文件刚被改过，请稍后重试", "打开配置文件");
                return;
            }
            new OpenFileDescriptor(project, file[0], 0).navigate(true);
        }
    }

    /**
     * 整页正文
     * <p>
     * Swing 的 HTML 支持停在 3.2，能用的就是标题、段落、{@code ul} / {@code ol} 列表、
     * {@code b}、{@code tt}、{@code pre} 这些。<b>不要写表格</b>：参数名加一句说明的宽度在
     * 侧边栏里排不开，列会被压成竖着的一串字。
     * </p>
     */
    private static String html() {
        return "<html><body>"
                + intro()
                + menu()
                + tabs()
                + attributes()
                + priority()
                + example()
                + "</body></html>";
    }

    private static String intro() {
        return "<h3>TreeInfotip 目录树备注</h3>"
                + "<p>给项目树上的文件和目录加备注、颜色、图标、悬浮提示、删除线，也能改掉节点显示的名字。"
                + "配置全部存在<b>项目根目录的 DirectoryV3.xml</b> 里，跟着项目走，"
                + "提交进版本库整个团队就能共用。</p>";
    }

    /**
     * 七项菜单文字要和 {@code plugin.xml} 里 {@code TreeInfotip.MenuGroup} 的 {@code text=} 一致，
     * 改菜单文字时这里也要跟着改
     */
    private static String menu() {
        return "<h4>怎么加备注</h4>"
                + "<p>在项目树上选中文件或目录（按住 Ctrl 可以多选），右键 → <b>目录备注</b>：</p>"
                + "<ul>"
                + "<li><b>添加文字备注</b>：节点名后面跟一段灰色的说明文字</li>"
                + "<li><b>覆盖显示名称</b>：整个换掉节点显示的名字，留空恢复原名</li>"
                + "<li><b>设置悬浮提示</b>：鼠标停在节点上时弹出的提示，可以写多行</li>"
                + "<li><b>添加颜色或图标</b>：换图标、改文字颜色和背景色</li>"
                + "<li><b>添加/取消删除线</b>：给节点名加一道删除线，用来标记废弃的东西</li>"
                + "<li><b>按扩展名批量设置</b>：一条规则管一批同扩展名的文件，"
                + "范围可以限定在选中的目录，也可以是整个项目</li>"
                + "<li><b>清除全部设置</b>：把选中节点的备注和样式全删掉</li>"
                + "</ul>"
                + "<p>存盘立刻生效，不用重启 IDE。目录树只在<b>重绘</b>时才应用新样式，"
                + "偶尔看着没变，把上一级目录折叠再展开一下就好。</p>";
    }

    private static String tabs() {
        return "<h4>侧边栏的三个 tab</h4>"
                + "<ul>"
                + "<li><b>文件成员</b>：当前编辑的那个文件里的方法和属性，每项后面跟着它的注释。"
                + "<b>单击</b>跳到定义处，<b>双击</b>收缩或展开。上面的「方法」「属性」勾选框控制显示哪一类，"
                + "「层数」拉杆控制展开几层，最多 10 层。注释按四级找：文档注释、多行注释、"
                + "行尾的单行注释、紧贴在上方的单行注释，命中一级就不再往下找。</li>"
                + "<li><b>目录备注</b>：DirectoryV3.xml 里的规则一行一条。<b>双击</b>跳到对应的文件；"
                + "路径已经不存在的<b>标红排在最前面</b>，双击跳到 XML 里那条规则所在的行；"
                + "被前面同路径规则盖住、永远不生效的<b>标灰</b>。"
                + "右键有<b>置顶</b>和<b>删除</b>，都支持 Ctrl 多选；工具栏上还有「刷新」"
                + "「清除失效路径」和「清理重复规则」。</li>"
                + "<li><b>说明</b>：就是这一页。</li>"
                + "</ul>"
                + "<p>另外底部还有一个「TreeInfotip XML」窗口，直接编辑配置文件本身。</p>";
    }

    /**
     * 九个参数要和 {@link com.plugins.infotip.storage.XmlStorage} 里的常量对得上，
     * 新增可配置属性时这里也要补一条
     */
    private static String attributes() {
        return "<h4>DirectoryV3.xml 的参数</h4>"
                + "<p>一条 &lt;tree&gt; 就是一条规则，参数<b>全是可选的</b>，按需要写几个：</p>"
                + "<ul>"
                + "<li><tt>path</tt> — 相对项目根目录的路径，以 / 开头，例如 <tt>/src/main/java</tt>；"
                + "只写 <tt>/</tt> 表示整个项目。末尾多写的 / 会被忽略。</li>"
                + "<li><tt>extension</tt> — 扩展名，<b>不带点</b>，例如 <tt>java</tt>。"
                + "只作用于文件，目录节点不参与；和 <tt>path</tt> 一起写，表示这个目录"
                + "连各级子目录下的这类文件。</li>"
                + "<li><tt>title</tt> — 备注文字，灰色跟在节点名后面。</li>"
                + "<li><tt>presentableText</tt> — 覆盖节点显示的名字。</li>"
                + "<li><tt>tooltipTitle</tt> — 鼠标悬浮时的提示，可以写多行。</li>"
                + "<li><tt>icon</tt> — 换图标，填 AllIcons 里的字段路径，例如 <tt>Nodes.Folder</tt>。</li>"
                + "<li><tt>textColor</tt> — 文字颜色，十进制 <tt>r,g,b</tt>，例如 <tt>255,0,0</tt>。</li>"
                + "<li><tt>backgroundColor</tt> — 背景色，写法同上。</li>"
                + "<li><tt>strikethrough</tt> — 填 <tt>true</tt> 给节点加删除线。</li>"
                + "</ul>";
    }

    private static String priority() {
        return "<h4>命中优先级</h4>"
                + "<p>同一个节点被多条规则命中时，从高到低：</p>"
                + "<ol>"
                + "<li><b>路径规则</b>（只写 <tt>path</tt>）：路径全等的那一个文件或目录</li>"
                + "<li><b>目录级类型规则</b>（<tt>path</tt> 加 <tt>extension</tt>）：该目录及各级子目录下的"
                + "这类文件，多条同时命中时 <b>path 更长的赢</b></li>"
                + "<li><b>全项目类型规则</b>（只写 <tt>extension</tt>）：整个项目的这类文件，只做兜底</li>"
                + "</ol>"
                + "<p>同优先级时<b>写在前面的那条赢</b>，所以「目录备注」里的「置顶」不是列表排序，"
                + "是真的把这条标签挪到文件最前面。</p>"
                + "<p>也因此，<tt>path</tt> 和 <tt>extension</tt> 都一样的<b>第二条规则完全没用</b>——"
                + "不是「部分生效」，哪怕它多配了前面那条没有的参数也读不到。这种规则在「目录备注」里"
                + "标灰，用工具栏的「清理重复规则」一键删掉，在生效的那条会保留。</p>";
    }

    /**
     * {@code <pre>} 不折行，横向滚动条又关掉了，所以每行都要压在 35 个字符左右
     */
    private static String example() {
        return "<h4>例子</h4>"
                + "<pre>"
                + "&lt;trees&gt;\n"
                + "  &lt;tree path=\"/src\" title=\"源码\"/&gt;\n"
                + "  &lt;tree path=\"/old\"\n"
                + "        strikethrough=\"true\"/&gt;\n"
                + "  &lt;tree path=\"/api\" extension=\"ts\"\n"
                + "        textColor=\"255,0,0\"/&gt;\n"
                + "&lt;/trees&gt;"
                + "</pre>";
    }
}
