package com.plugins.infotip.gui.view;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.plugins.infotip.PluginStartupActivity;
import com.plugins.infotip.gui.ColorsUtils;
import com.plugins.infotip.gui.IconsUtils;
import com.plugins.infotip.gui.compone.MyTreeNode;
import com.plugins.infotip.storage.NodePaths;
import com.plugins.infotip.storage.XmlEntity;
import com.plugins.infotip.storage.XmlFileUtils;
import com.plugins.infotip.storage.XmlStorage;
import com.plugins.infotip.trees.TreesUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A <code>NoteTreeView</code> Class
 * <p>
 * 「目录备注」列表：{@code DirectoryV7.xml} 里配的规则按目录层级组织成一棵树（7.0.2 起）。
 * </p>
 * <p>
 * 7.0.2 之前是平铺一行一条。当初不做树是因为规则稀疏，一条 {@code /src/main/java/a/b/C.java}
 * 在树里要建五层空目录才挂得上。7.0.0 配置本身改成嵌套后这个顾虑没了：单链目录会压平成一段
 * （{@code src/main/java} 一个节点而非三层），树的结构和配置文件、项目树都对得上。
 * 5.4.1 之前「备注列表（双击刷新）」的根节点仍不显示，刷新在工具栏上。
 * </p>
 * <p>
 * 路径已失效的规则留在它本来的层级位置标红，同时把祖先链自动展开（{@code makeVisible}），
 * 项目树上已经没有的节点照样一眼看得到。这个列表是用户唯一能发现并清掉它们的地方，
 * 所以还给了工具栏上的「清除失效路径」一键删。
 * </p>
 * <p>
 * 被前面同「路径 + 扩展名」的规则盖住、永远不会生效的那些标灰显示（多半是手改配置时复制粘贴
 * 留下的），对应工具栏上的「清理重复规则」。只标不删：这文件在用户项目根目录里，会进版本库，
 * 插件不背着人重写它。
 * </p>
 * <p>
 * 只有 {@code path}、自己没配任何规则的<b>纯目录容器</b>节点只为撑起层级，用文件夹图标、
 * 没有 {@code XmlEntity}，单击靠 {@link com.plugins.infotip.gui.compone.MyTreeNode#getFullPath()} 跳到对应目录。
 * </p>
 * <p>
 * 被前面同「路径 + 扩展名」的规则盖住、永远不会生效的那些标灰显示（多半是手改配置时复制粘贴
 * 留下的），对应工具栏上的「清理重复规则」。只标不删：这文件在用户项目根目录里，会进版本库，
 * 插件不背着人重写它。
 * </p>
 * <p>
 * 5.5.0 起本类不再是 {@code ToolWindowFactory}——工具窗口有两个 tab 了，工厂搬去
 * {@link NotesToolWindowFactory}，这里只负责出一块面板。
 * </p>
 *
 * @author lk
 * @version 1.0
 * <p><b>date: 2023/4/13 21:03</b></p>
 */
public class NoteTreeView extends Tree {

    /**
     * 工具栏和右键菜单的 {@code place}，只用于 action 事件溯源，不是全局注册的 id
     */
    private static final String PLACE_TOOLBAR = "TreeInfotipNoteListToolbar";

    private static final String PLACE_POPUP = "TreeInfotipNoteListPopup";

    /**
     * 「清除失效路径」的扫把图标，放在 {@code resources/icons} 下，深色主题由平台的图标灰度反转自动适配
     */
    private static final Icon ICON_CLEAR_MISSING = IconLoader.getIcon("/icons/clearMissing.svg", NoteTreeView.class);

    /**
     * 「清理重复规则」的垃圾桶图标
     */
    private static final Icon ICON_CLEAR_SHADOWED = IconLoader.getIcon("/icons/clearShadowed.svg", NoteTreeView.class);

    private final Project project;

    /**
     * 本次 {@link #reload()} 里路径已失效的那些规则，「清除失效路径」直接删它们
     */
    private final List<XmlEntity> missingEntities = new ArrayList<>();

    /**
     * 本次 {@link #reload()} 里被前面同键规则盖住、永远不会生效的那些规则，「清理重复规则」直接删它们
     * <p>
     * 这里收的是<b>全部</b>重复规则，包含没有文字、没进列表的那些：它们在列表里看不见，
     * 但在文件里照样占着位置，一键清理时要一起带走。
     * </p>
     */
    private final List<XmlEntity> shadowedEntities = new ArrayList<>();

    /**
     * 展开层数上限。再深在这个宽度的侧边栏里也看不清了
     */
    private static final int MAX_DEPTH = 10;

    /**
     * 打开时默认展开几层
     */
    private static final int DEFAULT_DEPTH = 3;

    /**
     * 取不到系统双击间隔时的默认值，和 Swing 自己的默认一致
     */
    private static final int DEFAULT_CLICK_INTERVAL = 500;

    /**
     * 等第二下的时间上限：系统值太宽，等满了单击定位会显得卡，见 {@code MemberTreeView} 同名常量
     */
    private static final int MAX_CLICK_WAIT = 200;

    /**
     * 正排着队等双击间隔过去的那次定位，{@code null} 表示当前没有
     */
    private Timer pendingClick;

    /**
     * 当前选的展开层数，点上面那排数字改
     */
    private int currentDepth = DEFAULT_DEPTH;

    /**
     * 那排数字标签，选中的高亮。点几就把树展开到几层
     */
    private final List<JLabel> depthLabels = new ArrayList<>();

    /**
     * 本次 {@link #reload()} 里失效的那些树节点，用来把它们的祖先链展开，见 {@link #revealMissing}
     */
    private final List<MyTreeNode> missingNodes = new ArrayList<>();

    private NoteTreeView(@NotNull Project project) {
        super(new DefaultMutableTreeNode("备注列表"));
        this.project = project;
        //根节点不显示，但要留展开箭头：7.0.2 起列表按目录层级做成了树
        setRootVisible(false);
        setShowsRootHandles(true);
        setCellRenderer(new NoteCellRenderer());
    }

    /**
     * 建出「目录备注」这个 tab 的整块内容：上面一条工具栏，下面一个可滚动的列表
     *
     * @param project 当前项目
     * @return 直接塞给 {@code ContentFactory#createContent} 的组件
     */
    public static JComponent createPanel(@NotNull Project project) {
        final NoteTreeView view = new NoteTreeView(project);
        view.installNavigation();
        view.installPopupMenu();
        view.listenConfigChange();
        final SimpleToolWindowPanel panel = new SimpleToolWindowPanel(true, true);
        panel.setToolbar(view.createToolbar());
        //必须套一层滚动面板，否则备注条数超过工具窗口高度时只能看到前几条，滚不动
        panel.setContent(new JBScrollPane(view));
        view.reload();
        return panel;
    }

    /**
     * 顶部控件：一行里放按钮工具栏 + 「展开」+ 一排可点的层数数字
     */
    private JComponent createToolbar() {
        final DefaultActionGroup group = new DefaultActionGroup();
        group.add(new RefreshAction());
        group.add(new ClearMissingAction());
        group.add(new ClearShadowedAction());
        final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(PLACE_TOOLBAR, group, true);
        //不设 targetComponent 平台会警告，action 的 update 也拿不到正确的 DataContext
        toolbar.setTargetComponent(this);

        //按钮和「展开」层数数字放同一行
        final JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        row.add(toolbar.getComponent());
        row.add(buildDepthRow());
        return row;
    }

    /**
     * 「展开」+ 数字 1..10 的那一行
     * <p>
     * 用一排可点的数字而不是拉杆：树是<b>整棵建好</b>的，点数字只是把它展开到第几层，没展开的
     * 手动还能再展开，不像「文件成员」那样按层数重建。每个数字加个方框看着像按钮、也把这行的
     * 空白填起来；数字排在一行里、放得下几个是几个，最多到 {@link #MAX_DEPTH}。
     * </p>
     */
    private JComponent buildDepthRow() {
        final JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        row.add(new JLabel("展开"));
        depthLabels.clear();
        for (int i = 1; i <= MAX_DEPTH; i++) {
            final int depth = i;
            final JLabel label = new JLabel(String.valueOf(i), SwingConstants.CENTER);
            label.setToolTipText("展开到第 " + i + " 层");
            label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            label.setOpaque(true);
            label.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    currentDepth = depth;
                    applyDepth();
                    highlightDepth();
                }
            });
            depthLabels.add(label);
            row.add(label);
        }
        highlightDepth();
        return row;
    }

    /**
     * 当前层数那个数字高亮：加粗、描边和底色都换一下，其余的用普通描边
     */
    private void highlightDepth() {
        for (int i = 0; i < depthLabels.size(); i++) {
            final JLabel label = depthLabels.get(i);
            final boolean selected = (i + 1) == currentDepth;
            label.setFont(label.getFont().deriveFont(selected ? Font.BOLD : Font.PLAIN));
            label.setForeground(selected ? JBColor.foreground() : JBColor.gray);
            label.setBackground(selected ? UIUtil.getListSelectionBackground(false) : UIUtil.getPanelBackground());
            final Color line = selected ? JBColor.namedColor("Component.focusColor", JBColor.BLUE) : JBColor.border();
            label.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(line), JBUI.Borders.empty(0, 5)));
        }
    }

    /**
     * 把树展开到 {@link #currentDepth} 层：层数小于它的节点全展开、到了就收起，
     * 失效节点的祖先链无论层数都展开（{@link #revealMissing}）
     */
    private void applyDepth() {
        final DefaultMutableTreeNode root = (DefaultMutableTreeNode) getModel().getRoot();
        setExpanded(new TreePath(root), root);
        revealMissing();
    }

    /**
     * 递归按层数展开 / 收起。根是第 0 层、不可见，它的孩子算第 1 层
     */
    private void setExpanded(TreePath path, DefaultMutableTreeNode node) {
        if (node.getLevel() < currentDepth) {
            expandPath(path);
            for (int i = 0; i < node.getChildCount(); i++) {
                final DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
                setExpanded(path.pathByAddingChild(child), child);
            }
        } else if (node.getLevel() > 0) {
            //根不可见也不能收，收了整棵树就没了
            collapsePath(path);
        }
    }

    /**
     * 把本次失效的节点的祖先链展开，藏得再深也让它露出来
     */
    private void revealMissing() {
        for (MyTreeNode node : missingNodes) {
            makeVisible(new TreePath(node.getPath()));
        }
    }


    /**
     * 右键菜单：置顶 + 删除，整行都能点，不只是文字上
     * <p>
     * 自己继承 {@link PopupHandler} 而不用 {@code installFollowingSelectionTreePopup}：那个方法
     * 的判据是 {@code getPathForLocation(x, y) != null && 这一行在选中里}，前半句是<b>认横坐标</b>
     * 的——点在标签文字右边的空白处返回 {@code null}，菜单就弹不出来。基类只负责
     * {@code isPopupTrigger()} 的跨平台差异（Windows 在松开时触发、X11 / macOS 在按下时），
     * 剩下的判定全在这里，所以换掉它没有副作用。
     * </p>
     * <p>
     * 选中本身<b>不用管</b>：{@link Tree} 自己的 {@code MyMouseListener} 已经用
     * {@code getClosestPathForLocation} 处理好了——右键落在没选中的行上就把选中改成那一行，
     * 落在已选中的行上则整批多选原样保留，所以 Ctrl 多选之后右键，{@link #selectedEntities()}
     * 拿到的仍是整批。它在 {@code Tree} 的构造里就注册了，比这里装的监听先跑。这里再兜一次
     * {@code setSelectionRow} 只为极端情况（选中被别处清掉）留个底。
     * </p>
     * <p>
     * 别换回 {@code PopupHandler.installPopupHandler(...)}，那几个重载在新版平台上全带删除标记，
     * Plugin Verifier 会报出来。
     * </p>
     */
    private void installPopupMenu() {
        final DefaultActionGroup group = new DefaultActionGroup();
        group.add(new MoveToTopAction());
        group.add(new DeleteAction());
        addMouseListener(new PopupHandler() {
            @Override
            public void invokePopup(Component comp, int x, int y) {
                final int row = rowAt(y);
                if (row < 0) {
                    return;
                }
                if (!isRowSelected(row)) {
                    setSelectionRow(row);
                }
                ActionManager.getInstance().createActionPopupMenu(PLACE_POPUP, group)
                        .getComponent().show(comp, x, y);
            }
        });
    }

    /**
     * 只看纵坐标落在哪一行，行尾的空白处也算这一行
     * <p>
     * {@code getRowForLocation} 认横坐标，标签文字之外一律返回 -1，所以走
     * {@code getClosestRowForLocation}（基本只看 y）再用 {@link #getRowBounds} 卡一下范围：
     * 不卡的话点在最后一行下方的大片空白上会算成最后一行，右键就成了对着看不见的行操作。
     * </p>
     *
     * @return 行号，纵坐标不在任何一行上时返回 -1
     */
    private int rowAt(int y) {
        final int row = getClosestRowForLocation(0, y);
        if (row < 0) {
            return -1;
        }
        final Rectangle bounds = getRowBounds(row);
        return null != bounds && y >= bounds.y && y < bounds.y + bounds.height ? row : -1;
    }

    /**
     * XML 一变就重建列表：自己点菜单改的、用户手改文件的都会走到这里，启动读完配置也回调一次
     */
    private void listenConfigChange() {
        final XmlFileUtils.SaveCallback saveCallback = this::reload;
        final PluginStartupActivity.RunCallback runCallback = saveCallback::run;
        PluginStartupActivity.ListenerRun(project, runCallback);
        XmlFileUtils.ListenerSave(project, saveCallback);
    }

    /**
     * 单击定位、双击展开 / 收起
     * <p>
     * 双击一个有子节点的目录，树自己会切换展开状态，所以定位不能也挂在双击上——那样双击目录会
     * 「又展开又定位」。改成<b>单击定位</b>：叶子（文件）没有展开状态，单击立刻跳；有子节点的目录
     * <b>等一小会儿</b>（{@link #MAX_CLICK_WAIT}），期间来了第二下就把排着的定位撤掉、只留展开。
     * 这套和「文件成员」列表是同一套做法，见那边的注释。
     * </p>
     */
    private void installNavigation() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (MouseEvent.BUTTON1 != e.getButton()) {
                    cancelPending();
                    return;
                }
                final TreePath path = getPathForLocation(e.getX(), e.getY());
                if (null == path || !(path.getLastPathComponent() instanceof MyTreeNode)) {
                    //点在展开箭头或空白处，交给树自己处理
                    return;
                }
                //第二下先把第一下排的队撤掉，剩下的展开 / 收起由树自己做
                cancelPending();
                if (1 != e.getClickCount()) {
                    return;
                }
                final MyTreeNode node = (MyTreeNode) path.getLastPathComponent();
                if (node.isLeaf()) {
                    navigateNode(node);
                    return;
                }
                pendingClick = delayed(node);
            }
        });
    }

    /**
     * 排一个延后的定位：等 {@link #MAX_CLICK_WAIT} 那么久，没被第二下打断就跳
     */
    private Timer delayed(MyTreeNode node) {
        //javax.swing.Timer 的回调本来就在 EDT 上
        final Timer timer = new Timer(clickInterval(), e -> {
            pendingClick = null;
            navigateNode(node);
        });
        timer.setRepeats(false);
        timer.start();
        return timer;
    }

    private void cancelPending() {
        if (null != pendingClick) {
            pendingClick.stop();
            pendingClick = null;
        }
    }

    private static int clickInterval() {
        final Object value = Toolkit.getDefaultToolkit().getDesktopProperty("awt.multiClickInterval");
        final int system = value instanceof Integer && (Integer) value > 0 ? (Integer) value : DEFAULT_CLICK_INTERVAL;
        //系统值只当上限：它比人手双击的实际间隔宽得多，等满了会显得卡
        return Math.min(system, MAX_CLICK_WAIT);
    }

    /**
     * 定位一个节点：带 entity 的跳规则、纯容器按路径跳目录
     */
    private void navigateNode(MyTreeNode node) {
        final Object obj = node.getUserEntity();
        if (obj instanceof XmlEntity) {
            navigate(project, (XmlEntity) obj, node.isShadowed());
        } else if (!trimmed(node.getFullPath()).isEmpty()) {
            TreesUtils.Navigation(project, node.getFullPath());
        }
    }

    /**
     * 重建整棵树：按规则路径的目录层级组织，失效的规则在原位标红、其祖先链自动展开
     * <p>
     * 7.0.2 之前是平铺一行一条。配置本身在 7.0.0 改成了嵌套结构，列表跟着做成树，结构和
     * 配置文件、项目树都对得上。单链目录会压平成一段（{@code src/main/java} 一个节点而非三层），
     * 所以不会出现当初担心的「一条深路径撑出五层空目录」。
     * </p>
     * <p>
     * 失效的规则不再拎到最前面，而是留在它本来的位置标红——但会把它的祖先链自动展开
     * （`makeVisible`），项目树上已经没有的节点照样一眼看得到。
     * </p>
     * <p>
     * 顺手按 {@link TreesUtils#ruleKey} 查重：键相同的第二条及以后的规则被前面那条完全盖住，
     * 一辈子不会生效，标灰显示并攒进 {@link #shadowedEntities}。只标不删。
     * </p>
     */
    private void reload() {
        final DefaultTreeModel model = (DefaultTreeModel) getModel();
        final DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
        root.removeAllChildren();
        missingEntities.clear();
        shadowedEntities.clear();
        missingNodes.clear();

        final Seg segRoot = new Seg("");
        final List<XmlEntity> entities = XmlStorage.getXmlEntity(project);
        if (null != entities) {
            final Set<String> seen = new HashSet<>();
            for (XmlEntity entity : entities) {
                //键要在建节点之前登记：后面同键的那条确实不生效
                final boolean shadowed = !seen.add(TreesUtils.ruleKey(entity));
                if (shadowed) {
                    shadowedEntities.add(entity);
                }
                Seg cursor = segRoot;
                for (String segment : NodePaths.segments(entity.getPath())) {
                    cursor = cursor.child(segment);
                }
                cursor.rules.add(new Rule(entity, shadowed));
            }
        }

        for (Seg child : segRoot.children.values()) {
            root.add(toNode(child, "", missingNodes));
        }
        //只写 extension 的全项目类型规则挂在根 Seg 上，直接铺在最外层
        for (Rule rule : segRoot.rules) {
            root.add(leaf(rule, missingNodes));
        }
        //reload 要放在加完子节点之后：root.add 不发 model 事件
        model.reload();
        //展开到当前层数，并把失效节点的祖先链无条件展开
        applyDepth();
    }

    /**
     * 把一个 {@link Seg} 转成树节点，沿途压平单链的纯容器
     * <p>
     * 一个路径上可能既有目录自己的备注（路径规则），又有限定在这个目录的类型规则，还有子目录。
     * 目录节点承载路径规则，类型规则作为子叶子跟在后面，子目录递归展开——和配置文件的嵌套一致。
     * </p>
     *
     * @param parentPath 父节点的完整路径，最外层传空串
     * @param reveal     收集需要展开祖先的失效节点
     */
    private MyTreeNode toNode(Seg seg, String parentPath, List<MyTreeNode> reveal) {
        //压平：自己没规则、又只有一个孩子的纯容器，和孩子并成一段
        String label = seg.segment;
        Seg node = seg;
        while (node.rules.isEmpty() && node.children.size() == 1) {
            final Seg only = node.children.values().iterator().next();
            label = label + "/" + only.segment;
            node = only;
        }
        final String fullPath = NodePaths.join(parentPath, label);

        //这一层的第一条规则落在节点自己身上，其余的（重复项）当子叶子
        final Rule own = node.rules.isEmpty() ? null : node.rules.get(0);
        final MyTreeNode result = dirNode(label, fullPath, own, reveal);
        //被盖住的重复路径规则铺成子叶子，再铺子目录
        for (Rule rule : node.rules) {
            if (rule != own) {
                result.add(leaf(rule, reveal));
            }
        }
        for (Seg child : node.children.values()) {
            result.add(toNode(child, fullPath, reveal));
        }
        return result;
    }

    /**
     * 建目录 / 文件节点本体
     *
     * @param own 这个路径自己的规则，纯容器传 {@code null}
     */
    private MyTreeNode dirNode(String label, String fullPath, Rule own, List<MyTreeNode> reveal) {
        if (null == own) {
            //纯容器：只为撑起层级，用文件夹图标、不查失效，单击靠 fullPath 跳目录
            return new MyTreeNode(label).setFullPath(fullPath).setIcon(fit(AllIcons.Nodes.Folder));
        }
        final XmlEntity entity = own.entity;
        final String text = firstNonEmpty(trimmed(entity.getNote()), trimmed(entity.getLabel()), label);
        final MyTreeNode result = new MyTreeNode(text).setUserEntity(entity)
                .setFullPath(fullPath).setShadowed(own.shadowed);
        final VirtualFile file = TreesUtils.findProjectFile(project, entity.getPath());
        if (null == file) {
            //失效优先：路径没了就用错误图标，不管它配没配图标
            result.setMissing(true).setIcon(fit(AllIcons.General.Error));
            missingEntities.add(entity);
            reveal.add(result);
            return result;
        }
        final Icon structural = fit(file.isDirectory() ? AllIcons.Nodes.Folder
                : FileTypeManager.getInstance().getFileTypeByFileName(file.getName()).getIcon());
        return result.setIcon(ruleIcon(entity, structural));
    }

    /**
     * 建一条规则的叶子节点：目前只有被前面同路径规则盖住的重复项、以及挂在根上的空路径规则会走这里
     */
    private MyTreeNode leaf(Rule rule, List<MyTreeNode> reveal) {
        final XmlEntity entity = rule.entity;
        final String scope = trimmed(entity.getPath());
        final String text = firstNonEmpty(trimmed(entity.getNote()), trimmed(entity.getLabel()), "");
        //没写文字就拿路径兜底，空路径（根规则）显示成「/」
        final String display = !text.isEmpty() ? text : (scope.isEmpty() ? "/" : scope);
        final MyTreeNode result = new MyTreeNode(display).setUserEntity(entity)
                .setFullPath(scope).setShadowed(rule.shadowed);
        //空路径的根规则本来就匹配不到具体文件，不算失效
        final VirtualFile file = scope.isEmpty() ? null : TreesUtils.findProjectFile(project, entity.getPath());
        if (!scope.isEmpty() && null == file) {
            result.setMissing(true).setIcon(fit(AllIcons.General.Error));
            missingEntities.add(entity);
            reveal.add(result);
            return result;
        }
        final Icon structural = fit(null != file && !file.isDirectory()
                ? FileTypeManager.getInstance().getFileTypeByFileName(file.getName()).getIcon()
                : AllIcons.Nodes.Folder);
        return result.setIcon(ruleIcon(entity, structural));
    }

    /**
     * 规则配了图标就用它，好让侧边栏和项目树看着一致；没配就用表示「作用在什么上」的结构图标
     * （目录 / 文件类型）。和 {@link com.plugins.infotip.trees.TreesStyle} 取图标用的是同一个方法
     */
    private static Icon ruleIcon(XmlEntity entity, Icon structural) {
        final Icon configured = IconsUtils.findFitIcon(entity.getIcon());
        return null != configured ? configured : structural;
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (null != value && !value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    /**
     * 建树用的中间节点，一个 Seg 对应一段目录名
     */
    private static class Seg {

        private final String segment;

        private final java.util.LinkedHashMap<String, Seg> children = new java.util.LinkedHashMap<>();

        /**
         * 完整路径正好等于这个 Seg 的规则，通常一条；手改配置复制粘贴出重复项时会有多条
         */
        private final List<Rule> rules = new ArrayList<>();

        Seg(String segment) {
            this.segment = segment;
        }

        Seg child(String segment) {
            return children.computeIfAbsent(segment, Seg::new);
        }
    }

    /**
     * 一条规则加上它是否被前面同键规则盖住
     */
    private static class Rule {

        private final XmlEntity entity;

        private final boolean shadowed;

        Rule(XmlEntity entity, boolean shadowed) {
            this.entity = entity;
            this.shadowed = shadowed;
        }
    }

    /**
     * 光标落在哪条规则上，没选中或选中的不是规则时返回 {@code null}
     */
    private XmlEntity selectedEntity() {
        final Object component = getLastSelectedPathComponent();
        if (!(component instanceof MyTreeNode)) {
            return null;
        }
        final Object entity = ((MyTreeNode) component).getUserEntity();
        return entity instanceof XmlEntity ? (XmlEntity) entity : null;
    }

    /**
     * 选中的全部规则，按住 Ctrl 多选可以一次删掉或置顶一批
     * <p>
     * 按行号从上往下走，而不是读 {@code getSelectionPaths()}：那个返回的是点选的先后顺序，
     * Ctrl 多选时和列表里看到的上下顺序不一定一致，而「置顶」要保持这批规则原来的相对顺序。
     * </p>
     */
    private List<XmlEntity> selectedEntities() {
        final List<XmlEntity> entities = new ArrayList<>();
        for (int row = 0; row < getRowCount(); row++) {
            if (!isRowSelected(row)) {
                continue;
            }
            final TreePath path = getPathForRow(row);
            if (null == path) {
                continue;
            }
            final Object component = path.getLastPathComponent();
            if (!(component instanceof MyTreeNode)) {
                continue;
            }
            final Object entity = ((MyTreeNode) component).getUserEntity();
            if (entity instanceof XmlEntity) {
                entities.add((XmlEntity) entity);
            }
        }
        return entities;
    }

    /**
     * 定位一条备注：能落到真实文件就跳文件，否则跳到 {@code DirectoryV7.xml} 里这条规则所在的行
     * <p>
     * 跳 XML 覆盖两种在项目树上定位不到的情况：路径已经被删或改名的（列表里标红那些）、
     * 被前面同路径规则覆盖而不生效的（列表里标灰加删除线那些）。
     * </p>
     * <p>
     * 这里重新查一次 VFS 而不是看 {@link MyTreeNode#isMissing()}：那个状态是建节点时算的，
     * 建完之后在 IDE 外面删文件不会刷新，会把已经失效的当成有效去跳，结果又是没反应。
     * </p>
     * <p>
     * 同样不能看 {@link MyTreeNode#isShadowed()}：被覆盖的规则路径可能还在（只是不生效），
     * 跳到项目文件没意义，应该跳到配置文件让用户决定留还是删。
     * </p>
     */
    private static void navigate(Project project, XmlEntity entity, boolean shadowed) {
        //被覆盖的规则直接跳配置文件，不管路径在不在
        if (shadowed) {
            navigateToRule(project, entity);
            return;
        }
        final String path = entity.getPath();
        if (null != path && !path.trim().isEmpty() && null != TreesUtils.findProjectFile(project, path)) {
            TreesUtils.Navigation(project, path);
            return;
        }
        navigateToRule(project, entity);
    }

    /**
     * 打开 {@code DirectoryV7.xml} 并把光标放到这条 {@code <tree>} 标签上
     * <p>
     * 偏移量来自解析时存在 {@link XmlEntity} 上的 {@link XmlTag}，所以行号一定对得上，
     * 不用自己去文本里找。标签失效（文件被外部改过、还没重新解析完）时退到文件开头。
     * </p>
     */
    private static void navigateToRule(Project project, XmlEntity entity) {
        //取 PSI 的偏移量必须在读操作里：新版平台的 EDT 不再隐式持有读锁
        final VirtualFile[] file = {null};
        final int[] offset = {0};
        ApplicationManager.getApplication().runReadAction(() -> {
            final XmlTag tag = entity.getTag();
            if (null != tag && tag.isValid()) {
                final PsiFile containing = tag.getContainingFile();
                if (null != containing) {
                    file[0] = containing.getVirtualFile();
                    offset[0] = tag.getTextOffset();
                    return;
                }
            }
            final XmlFile xmlFile = XmlFileUtils.getXmlFile(project);
            if (null != xmlFile) {
                file[0] = xmlFile.getVirtualFile();
            }
        });
        //打开编辑器要在 EDT 上、读操作外面
        if (null != file[0]) {
            new OpenFileDescriptor(project, file[0], offset[0]).navigate(true);
        }
    }

    /**
     * 统一缩到 16。{@code AllIcons} 里有 32×15、18×22 这种，不缩会把列表行高撑起来
     */
    private static Icon fit(Icon icon) {
        return null == icon ? AllIcons.FileTypes.Any_type : IconsUtils.fit(icon);
    }

    private static String trimmed(String value) {
        return null == value ? "" : value.trim();
    }

    /**
     * 删除是不可逆的（改的是用户项目里的 {@code DirectoryV7.xml}），所以两个删除动作都要先问一句
     *
     * @return 用户点了「确定」才是 {@code true}
     */
    private boolean confirm(String message, String title) {
        //默认焦点放在「取消」（下标 1）上，避免顺手一个回车就删了
        final int choice = Messages.showDialog(project, message, title,
                new String[]{"确定", "取消"}, 1, Messages.getInformationIcon());
        return 0 == choice;
    }

    /**
     * 真正执行删除，顺手把列表刷掉
     * <p>
     * 存盘会触发 {@link XmlFileUtils#ListenerSave} 里注册的回调，也就是 {@link #reload()}，
     * 这里再显式刷一次是为了兜住「XML 里本来就没有这条标签」的情况——那时文件没变、
     * 回调不会来，但列表得把它去掉。
     * </p>
     *
     * @param title 少删了几条时弹的那个提示框的标题，跟着触发它的按钮走
     */
    private void remove(List<XmlEntity> targets, String title) {
        final int removed = XmlStorage.removeByTag(project, targets);
        reload();
        if (removed < targets.size()) {
            //标签失效（文件被外部改过、还没重新解析完）时会少删，说清楚让用户刷新后重试
            Messages.showDialog(project, "有 " + (targets.size() - removed) + " 条没能删掉，配置文件可能刚被改过，请刷新后重试",
                    title, new String[]{"知道了"}, 0, Messages.getWarningIcon());
        }
    }

    /**
     * 工具栏上的「刷新」
     * <p>
     * 路径存不存在只在 {@link #buildNode} 里查一次，在 IDE 外面删文件没有 PSI 事件，
     * 就靠这个按钮手动重查。5.4.1 之前这个功能挂在根节点的双击上。
     * </p>
     */
    private class RefreshAction extends AnAction {

        RefreshAction() {
            super("刷新", "重新检查所有规则的路径还在不在", AllIcons.Actions.Refresh);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            reload();
        }
    }

    /**
     * 工具栏上的「清除失效路径」，一次删掉列表里标红的全部规则
     */
    private class ClearMissingAction extends AnAction {

        ClearMissingAction() {
            super("清除失效路径", "删掉所有路径已经不存在的规则", ICON_CLEAR_MISSING);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            //先拷一份：removeByTag 会存盘，存盘回调里的 reload() 把 missingEntities 清空了
            final List<XmlEntity> targets = new ArrayList<>(missingEntities);
            if (targets.isEmpty()) {
                Messages.showInfoMessage(project, "当前没有路径已失效的规则", "清除失效路径");
                return;
            }
            if (!confirm("确定要删除这 " + targets.size() + " 条路径已失效的规则吗？", "清除失效路径")) {
                return;
            }
            remove(targets, "清除失效路径");
        }
    }

    /**
     * 工具栏上的「清理重复规则」，一次删掉列表里标灰的全部规则
     * <p>
     * 删的是「路径 + 扩展名」都和前面某条一样的第二条及以后（见 {@link TreesUtils#ruleKey}），
     * 真正在生效的那条第一名一定留着，所以清理前后项目树的显示<b>完全不变</b>。
     * </p>
     * <p>
     * 之所以要用户按一下、不在解析时自动去重：{@code DirectoryV7.xml} 躺在用户项目根目录里，
     * 是可以手改也会进版本库的文件，插件不该背着人重写它。
     * </p>
     */
    private class ClearShadowedAction extends AnAction {

        ClearShadowedAction() {
            super("清理重复规则", "删掉被前面同路径规则盖住、永远不会生效的那些规则", ICON_CLEAR_SHADOWED);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            //先拷一份：removeByTag 会存盘，存盘回调里的 reload() 把 shadowedEntities 清空了
            final List<XmlEntity> targets = new ArrayList<>(shadowedEntities);
            if (targets.isEmpty()) {
                Messages.showInfoMessage(project, "当前没有重复的规则", "清理重复规则");
                return;
            }
            if (!confirm("有 " + targets.size() + " 条规则和前面某条的路径、扩展名完全一样，"
                    + "永远不会生效。确定要删掉它们吗？\n（在生效的那条会保留，项目树的显示不会变）", "清理重复规则")) {
                return;
            }
            remove(targets, "清理重复规则");
        }
    }

    /**
     * 右键菜单里的「删除」，删掉选中的规则（按住 Ctrl 可以多选）
     */
    private class DeleteAction extends AnAction {

        DeleteAction() {
            super("删除", "从 DirectoryV7.xml 里删掉选中的规则", AllIcons.General.Remove);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            final List<XmlEntity> targets = selectedEntities();
            if (targets.isEmpty()) {
                return;
            }
            final String message = 1 == targets.size()
                    ? "确定要删除这条规则吗？"
                    : "确定要删除选中的这 " + targets.size() + " 条规则吗？";
            if (!confirm(message, "删除备注")) {
                return;
            }
            remove(targets, "删除备注");
        }
    }

    /**
     * 右键菜单里的「置顶」，把选中的 {@code <tree>} 挪到 {@code DirectoryV7.xml} 的最前面
     * <p>
     * 标签在文件里的先后是有意义的：同优先级的多条规则命中同一个节点时，
     * {@code TreesUtils.getMatchPath} 让先遇到的那条赢。所以「置顶」不是单纯的列表排序，
     * 是真的改文件。
     * </p>
     * <p>
     * 按住 Ctrl 多选可以一次置顶一批，它们原来的相对顺序会保持住，见
     * {@link XmlStorage#moveToTop(Project, List)}。
     * </p>
     */
    private class MoveToTopAction extends AnAction {

        MoveToTopAction() {
            super("置顶", "把选中的规则挪到配置文件最前面，同优先级时它们先生效", AllIcons.Actions.MoveUp);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            final List<XmlEntity> targets = selectedEntities();
            if (targets.isEmpty()) {
                return;
            }
            //已经在最前面时 moveToTop 返回 0，不用白刷一次列表
            if (XmlStorage.moveToTop(project, targets) > 0) {
                reload();
            }
        }
    }

    /**
     * 备注前面画类型图标，指向的路径已经不存在的整行标红，被前面同键规则盖住的整行标灰
     * <p>
     * 根节点不可见（{@code rootVisible=false}），所以不是 {@link MyTreeNode} 的节点直接跳过。
     * </p>
     * <p>
     * 失效优先于被覆盖：一条既失效又重复的规则先按红的显示，反正两种情况都该删。
     * </p>
     */
    private static class NoteCellRenderer extends ColoredTreeCellRenderer {

        @Override
        public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected,
                                          boolean expanded, boolean leaf, int row, boolean hasFocus) {
            if (!(value instanceof MyTreeNode)) {
                return;
            }
            final MyTreeNode node = (MyTreeNode) value;
            setIcon(node.getIcon());
            final String text = String.valueOf(node.getUserObject());
            if (node.isMissing()) {
                append(text, SimpleTextAttributes.ERROR_ATTRIBUTES);
                append("  路径已失效（单击定位到 XML）", SimpleTextAttributes.GRAYED_ATTRIBUTES);
            } else if (node.isShadowed()) {
                //规则文字本身加灰色和删除线，标记为"作废"状态
                final SimpleTextAttributes strikethroughGray = new SimpleTextAttributes(
                        SimpleTextAttributes.STYLE_STRIKEOUT, SimpleTextAttributes.GRAYED_ATTRIBUTES.getFgColor());
                append(text, strikethroughGray);
                //提示文字用橙色，"不生效"也加删除线
                final SimpleTextAttributes warning = new SimpleTextAttributes(
                        SimpleTextAttributes.STYLE_PLAIN, new JBColor(0xCC7832, 0xCC7832));
                final SimpleTextAttributes strikeoutWarning = new SimpleTextAttributes(
                        SimpleTextAttributes.STYLE_STRIKEOUT, new JBColor(0xCC7832, 0xCC7832));
                append("  被前面同路径的规则盖住，", warning);
                append("不生效", strikeoutWarning);
            } else {
                //正常规则：按它配的文字色和删除线渲染，让侧边栏成为项目树效果的预览。
                //失效（红）、被覆盖（灰）两种状态更要紧，仍旧盖过配色，走上面两个分支。
                append(text, ruleTextAttributes(node));
            }
        }

        /**
         * 由规则配置拼出文字样式：文字色 + 删除线。都没配就用默认前景色
         */
        private static SimpleTextAttributes ruleTextAttributes(MyTreeNode node) {
            final Object obj = node.getUserEntity();
            if (!(obj instanceof XmlEntity)) {
                return SimpleTextAttributes.REGULAR_ATTRIBUTES;
            }
            final XmlEntity entity = (XmlEntity) obj;
            final Color color = ColorsUtils.toColor(entity.getColor());
            final int style = entity.isStrikeEnabled()
                    ? SimpleTextAttributes.STYLE_STRIKEOUT : SimpleTextAttributes.STYLE_PLAIN;
            if (null == color && SimpleTextAttributes.STYLE_PLAIN == style) {
                return SimpleTextAttributes.REGULAR_ATTRIBUTES;
            }
            //color 为 null 时传 null，SimpleTextAttributes 会用默认前景色
            return new SimpleTextAttributes(style, color);
        }
    }
}
