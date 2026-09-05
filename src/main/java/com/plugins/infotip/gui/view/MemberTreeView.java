package com.plugins.infotip.gui.view;

import com.intellij.icons.AllIcons;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.plugins.infotip.gui.IconsUtils;
import com.plugins.infotip.gui.compone.MemberNode;
import com.plugins.infotip.psi.PsiCommentUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTree;
import javax.swing.Timer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * A <code>MemberTreeView</code> Class
 * <p>
 * 「文件成员」列表：把当前编辑的文件里的方法和属性列出来，每一项后面跟上它的注释。
 * </p>
 * <p>
 * IDE 自带的 Structure View 已经有这棵树了，缺的就是注释——所以这里不自己解析语法，
 * 直接借 Structure View 的模型（{@link StructureViewModel}）拿节点，再用
 * {@link PsiCommentUtils} 给每个节点补一段注释。这么做的好处是语言支持白拿：Java、
 * TS / JS / TSX / JSX、Kotlin、Python、Go……凡是 IDE 能出结构视图的都能出，
 * 而且不用在 {@code plugin.xml} 里加任何 {@code <depends>}。
 * </p>
 *
 * @author yc556&claude-opus-5
 * @version 1.0
 */
public class MemberTreeView extends Tree {

    /**
     * 工具栏的 {@code place}，只用于 action 事件溯源，不是全局注册的 id
     */
    private static final String PLACE_TOOLBAR = "TreeInfotipMemberListToolbar";

    /**
     * 层数上限。再深的树在这个宽度的侧边栏里已经没法看了
     */
    private static final int MAX_DEPTH = 10;

    private static final int DEFAULT_DEPTH = 3;

    /**
     * 节点数上限
     * <p>
     * 几千行的文件（打包压缩过的 js、生成的代码）结构树能有上万个节点，全建出来再全展开会把
     * EDT 卡住。到了上限就停下并在末尾补一行提示——这个列表是用来快速看一眼的，真要看全的
     * 去 IDE 自带的 Structure View。
     * </p>
     */
    private static final int MAX_NODES = 3000;

    /**
     * 取不到系统双击间隔（{@code awt.multiClickInterval}）时用的默认值，和 Swing 自己的默认值一致
     */
    private static final int DEFAULT_CLICK_INTERVAL = 500;

    /**
     * 等第二下的时间上限
     * <p>
     * 系统的 {@code awt.multiClickInterval} 在 Windows 上是 500ms，直接拿它当等待时间太久——
     * 点一个有子节点的成员要过半秒才跳，手感上像卡了一下（5.5.1 就是这样）。这两个值的含义本来
     * 就不一样：系统值说的是「最长多久还算一次双击」，而这里要的是「多久之后可以确定不会有第二下」，
     * 没必要等满。真正连着的两下基本落在 200ms 以内，所以取两者的较小值。
     * </p>
     */
    private static final int MAX_CLICK_WAIT = 200;

    //region 节点类别
    private static final int KIND_OTHER = 0;

    private static final int KIND_METHOD = 1;

    private static final int KIND_PROPERTY = 2;
    //endregion 节点类别

    private final Project project;

    private final JBCheckBox methodBox = new JBCheckBox("方法", true);

    private final JBCheckBox propertyBox = new JBCheckBox("属性", true);

    private final JSlider depthSlider = new JSlider(1, MAX_DEPTH, DEFAULT_DEPTH);

    private final JLabel depthLabel = new JLabel();

    /**
     * 本次 {@link #reload()} 已经建了多少个节点，用来卡 {@link #MAX_NODES}
     */
    private int nodes;

    /**
     * 正排着队等双击间隔过去的那次跳转，{@code null} 表示当前没有。见 {@link #installNavigation()}
     */
    private Timer pendingClick;

    private MemberTreeView(@NotNull Project project) {
        super(new DefaultMutableTreeNode("文件成员"));
        this.project = project;
        //根节点只是个容器，没必要显示；但要留出展开箭头的位置
        setRootVisible(false);
        setShowsRootHandles(true);
        setCellRenderer(new MemberCellRenderer());
    }

    /**
     * 建出「文件成员」这个 tab 的整块内容：上面两行控件，下面一棵可滚动的树
     *
     * @param project 当前项目
     * @return 直接塞给 {@code ContentFactory#createContent} 的组件
     */
    public static JComponent createPanel(@NotNull Project project) {
        final MemberTreeView view = new MemberTreeView(project);
        view.installNavigation();
        view.listenEditorChange();
        final SimpleToolWindowPanel panel = new SimpleToolWindowPanel(true, true);
        panel.setToolbar(view.createToolbar());
        panel.setContent(new JBScrollPane(view));
        view.reload();
        return panel;
    }

    /**
     * 顶部控件区：第一行是刷新按钮 + 两个勾选框，第二行是层数拉杆
     */
    private JComponent createToolbar() {
        final DefaultActionGroup group = new DefaultActionGroup();
        group.add(new RefreshAction());
        final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(PLACE_TOOLBAR, group, true);
        //不设 targetComponent 平台会警告，action 的 update 也拿不到正确的 DataContext
        toolbar.setTargetComponent(this);

        final JPanel first = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        first.add(toolbar.getComponent());
        first.add(methodBox);
        first.add(propertyBox);

        final JPanel second = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        second.add(new JLabel("层数"));
        second.add(depthSlider);
        second.add(depthLabel);

        final JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.add(first);
        box.add(second);
        installControlListeners();
        return box;
    }

    private void installControlListeners() {
        depthSlider.setMajorTickSpacing(1);
        depthSlider.setSnapToTicks(true);
        depthSlider.setToolTipText("展开几层成员，最多 " + MAX_DEPTH + " 层");
        updateDepthLabel();
        depthSlider.addChangeListener(e -> {
            //标签跟着拖动实时变，但重建整棵树太贵，只在松手之后做一次
            updateDepthLabel();
            if (!depthSlider.getValueIsAdjusting()) {
                reload();
            }
        });
        methodBox.setToolTipText("显示方法 / 函数");
        propertyBox.setToolTipText("显示属性 / 字段 / 变量");
        methodBox.addActionListener(e -> reload());
        propertyBox.addActionListener(e -> reload());
    }

    private void updateDepthLabel() {
        depthLabel.setText(depthSlider.getValue() + " 层");
    }

    /**
     * 切到别的文件就重读一遍
     * <p>
     * {@code connect(project)} 把连接挂在项目上，项目关掉自动断开，不用自己 dispose。
     * </p>
     */
    private void listenEditorChange() {
        project.getMessageBus().connect(project).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER,
                new FileEditorManagerListener() {
                    @Override
                    public void selectionChanged(@NotNull FileEditorManagerEvent event) {
                        reload();
                    }
                });
    }

    /**
     * 单击跳到成员的定义处，双击留给树自己收缩 / 展开
     * <p>
     * 5.5.0 把跳转挂在双击上，而树自己也在双击时切换展开状态，所以双击一个有子节点的成员会
     * 「又收缩又定位」，两件事一起发生。
     * </p>
     * <p>
     * 但不能简单地把判断从双击改成单击：一次双击的第一下也是单击，照跳不误，那个毛病一点没变。
     * 所以<b>有子节点的行要等一小会儿</b>（{@link #MAX_CLICK_WAIT}），期间来了第二下就把排着的
     * 跳转撤掉，只留下收缩。<b>叶子节点立刻跳</b>——它没有展开状态可切，双击对它没有别的含义，
     * 白等只会显得迟钝。
     * </p>
     * <p>
     * 节点从点击坐标取（{@link #getPathForLocation}）而不是从选中项取：点在展开箭头或行尾空白
     * 处时它返回 {@code null}，正好把「点箭头收缩」和「点成员跳转」分开；读选中项的话点箭头会
     * 跳到上一次选中的那个成员上去。
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
                if (null == path || !(path.getLastPathComponent() instanceof MemberNode)) {
                    //点在展开箭头、提示行或空白处，交给树自己处理
                    return;
                }
                //第二下先把第一下排的队撤掉，剩下的收缩由树自己做
                cancelPending();
                if (1 != e.getClickCount()) {
                    return;
                }
                final MemberNode node = (MemberNode) path.getLastPathComponent();
                if (node.isLeaf()) {
                    navigate(node.getElement());
                    return;
                }
                pendingClick = delayed(node);
            }
        });
    }

    /**
     * 排一个延后的跳转：等 {@link #MAX_CLICK_WAIT} 那么久，没被第二下打断就跳
     */
    private Timer delayed(MemberNode node) {
        //javax.swing.Timer 的回调本来就在 EDT 上，不用再自己切线程
        final Timer timer = new Timer(clickInterval(), e -> {
            pendingClick = null;
            navigate(node.getElement());
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
        //这个属性在有些桌面环境上取不到，也可能是别的类型
        final int system = value instanceof Integer && (Integer) value > 0 ? (Integer) value : DEFAULT_CLICK_INTERVAL;
        //系统值只当上限用：它比人手双击的实际间隔宽得多，等满了就显得卡，见 MAX_CLICK_WAIT
        return Math.min(system, MAX_CLICK_WAIT);
    }

    /**
     * 跳到一个成员的定义处
     * <p>
     * <b>不能直接调 {@code PsiElement.navigate(true)}</b>。它内部要先算出导航目标
     * （{@code EditSourceUtil.getDescriptor} → {@code getTextOffset()}），那一步就是读 PSI，
     * 而新版平台的 EDT 不再隐式持有读锁，2024.1 起直接抛
     * {@code Read access is allowed from inside read-action only}——5.5.0 就是这么炸的。
     * TS 的箭头函数尤其明显：{@code JSFunctionExpressionImpl.findNameIdentifier} 为了求偏移量
     * 要一路往上翻父节点。所以「navigate 必须在读操作外面调」这个说法只对
     * {@link OpenFileDescriptor} 成立，对 {@code PsiElement} 不成立。
     * </p>
     * <p>
     * 拆成两半：偏移量在读操作里取，开编辑器在读操作外面做。和 {@link NoteTreeView} 里跳
     * {@code DirectoryV3.xml} 是同一套写法。
     * </p>
     */
    private void navigate(PsiElement element) {
        if (null == element) {
            return;
        }
        final VirtualFile[] file = {null};
        final int[] offset = {0};
        //lambda 体必须写成语句块，写成赋值表达式会在 runReadAction 的三个重载之间歧义
        ApplicationManager.getApplication().runReadAction(() -> {
            if (!element.isValid()) {
                return;
            }
            final PsiFile containing = element.getContainingFile();
            if (null != containing) {
                file[0] = containing.getVirtualFile();
                offset[0] = element.getTextOffset();
            }
        });
        if (null == file[0]) {
            //PSI 已经失效（文件被外部改过），重读一遍再让用户点
            reload();
            return;
        }
        new OpenFileDescriptor(project, file[0], offset[0]).navigate(true);
    }

    /**
     * 重建整棵树。切文件、点刷新、改勾选、拖完拉杆都走这里
     */
    private void reload() {
        //整棵树都要重建，排着队的跳转指向的是旧节点，撤掉
        cancelPending();
        final DefaultMutableTreeNode root = (DefaultMutableTreeNode) getModel().getRoot();
        root.removeAllChildren();
        nodes = 0;
        //一个成员都没有时给一句话，让用户知道是没打开文件、还是这类文件不支持、还是勾选全关了
        final String[] hint = {""};
        ApplicationManager.getApplication().runReadAction(() -> {
            hint[0] = fill(root);
        });
        if (0 == root.getChildCount()) {
            root.add(new DefaultMutableTreeNode(hint[0]));
        }
        //root.add 不发 model 事件，reload 必须在加完子节点之后调
        ((DefaultTreeModel) getModel()).reload();
        //rootVisible=false 的树，根节点自己也要展开，否则一层都看不到
        expandPath(new TreePath(root));
        expandAll();
    }

    /**
     * 把当前文件的成员填进 {@code root}，返回填不出东西时该显示的提示
     * <p>
     * 必须在读操作里调。
     * </p>
     */
    private String fill(DefaultMutableTreeNode root) {
        final FileEditor editor = FileEditorManager.getInstance(project).getSelectedEditor();
        if (null == editor) {
            return "没有打开的文件";
        }
        final StructureViewBuilder builder = editor.getStructureViewBuilder();
        if (!(builder instanceof TreeBasedStructureViewBuilder)) {
            //不是基于树的 builder（图片、二进制之类的自定义编辑器）就拿不到节点，没别的办法
            return "这类文件没有结构信息";
        }
        //三个控件的值先取出来：下面的 try 里读不到局部作用域外的它们
        final int depth = depthSlider.getValue();
        final boolean methods = methodBox.isSelected();
        final boolean properties = propertyBox.isSelected();
        //createStructureViewModel(null) 不传 Editor：不需要跟随光标，只要一棵树
        final StructureViewModel structure = ((TreeBasedStructureViewBuilder) builder).createStructureViewModel(null);
        try {
            for (TreeElement child : structure.getRoot().getChildren()) {
                final MemberNode node = build(child, 1, depth, methods, properties);
                if (null != node) {
                    root.add(node);
                }
            }
        } finally {
            //StructureViewModel 是 Disposable，不 dispose 会漏掉它内部挂的监听
            structure.dispose();
        }
        if (nodes >= MAX_NODES) {
            root.add(new DefaultMutableTreeNode("（成员太多，只显示了前 " + MAX_NODES + " 项）"));
        }
        if (!methods && !properties) {
            return "勾选上面的「方法」或「属性」";
        }
        return "这个文件里没有可显示的成员";
    }

    private void expandAll() {
        //getRowCount 会随着展开一路变大，所以不能提前存下来
        for (int row = 0; row < getRowCount(); row++) {
            expandRow(row);
        }
    }

    /**
     * 递归把一个结构视图节点转成 {@link MemberNode}
     *
     * @param element    结构视图给的节点
     * @param depth      当前层数，从 1 开始
     * @param maxDepth   拉杆上设的层数上限
     * @param methods    要不要方法
     * @param properties 要不要属性
     * @return 不该显示时返回 {@code null}
     */
    private MemberNode build(TreeElement element, int depth, int maxDepth, boolean methods, boolean properties) {
        if (nodes >= MAX_NODES) {
            return null;
        }
        final List<MemberNode> children = new ArrayList<>();
        if (depth < maxDepth) {
            for (TreeElement child : element.getChildren()) {
                final MemberNode node = build(child, depth + 1, maxDepth, methods, properties);
                if (null != node) {
                    children.add(node);
                }
            }
        }
        final ItemPresentation presentation = element.getPresentation();
        final String name = trimmed(presentation.getPresentableText());
        if (name.isEmpty()) {
            return null;
        }
        final PsiElement psi = psiOf(element);
        //只在叶子上按勾选过滤：内层节点（类、接口）被滤掉的话，它下面的成员就成了孤儿
        if (children.isEmpty()) {
            final int kind = classify(psi, name);
            if (KIND_METHOD == kind && !methods) {
                return null;
            }
            if (KIND_PROPERTY == kind && !properties) {
                return null;
            }
        }
        nodes++;
        final MemberNode node = new MemberNode(name)
                .setIcon(fit(presentation.getIcon(false)))
                .setElement(psi)
                .setComment(PsiCommentUtils.read(psi));
        for (MemberNode child : children) {
            node.add(child);
        }
        return node;
    }

    private static PsiElement psiOf(TreeElement element) {
        if (!(element instanceof StructureViewTreeElement)) {
            return null;
        }
        final Object value = ((StructureViewTreeElement) element).getValue();
        return value instanceof PsiElement ? (PsiElement) value : null;
    }

    /**
     * 判断一个节点是方法还是属性
     * <p>
     * 这里<b>不认任何具体语言的 PSI 类</b>——认了就得在 {@code plugin.xml} 里加 {@code <depends>}，
     * 而且每多支持一种语言就得改一次。改成往上翻实现类和接口的<b>简单名</b>：Java 是
     * {@code PsiMethodImpl} / {@code PsiFieldImpl}，TS 是 {@code TypeScriptFunction}，
     * Kotlin 是 {@code KtNamedFunction} / {@code KtProperty}，Python 是 {@code PyFunction}……
     * 名字里带 Method / Function / Constructor 的算方法，带 Field / Property / Variable /
     * Constant 的算属性。
     * </p>
     * <p>
     * 两头都不沾的归 {@link #KIND_OTHER}，而 OTHER <b>永远显示</b>：宁可多显示几行，也不要
     * 因为认不出类别就把整个类连着它的方法一起藏掉。
     * </p>
     */
    private static int classify(PsiElement psi, String name) {
        if (null != psi) {
            for (Class<?> type = psi.getClass(); null != type && Object.class != type; type = type.getSuperclass()) {
                final int kind = kindOfName(type.getSimpleName());
                if (KIND_OTHER != kind) {
                    return kind;
                }
                for (Class<?> face : type.getInterfaces()) {
                    final int byFace = kindOfName(face.getSimpleName());
                    if (KIND_OTHER != byFace) {
                        return byFace;
                    }
                }
            }
        }
        //兜底：结构视图给方法的显示文字基本都带参数括号
        return name.contains("(") ? KIND_METHOD : KIND_OTHER;
    }

    private static int kindOfName(String simpleName) {
        if (simpleName.contains("Method") || simpleName.contains("Function") || simpleName.contains("Constructor")) {
            return KIND_METHOD;
        }
        if (simpleName.contains("Field") || simpleName.contains("Property")
                || simpleName.contains("Variable") || simpleName.contains("Constant")) {
            return KIND_PROPERTY;
        }
        return KIND_OTHER;
    }

    /**
     * 结构视图给的图标也可能超过 16，和目录树一样要缩到一行的高度
     */
    private static Icon fit(Icon icon) {
        return null == icon ? null : IconsUtils.fit(icon);
    }

    private static String trimmed(String text) {
        return null == text ? "" : text.trim();
    }

    private class RefreshAction extends AnAction {

        RefreshAction() {
            super("刷新", "重新读一遍当前文件的成员和注释", AllIcons.Actions.Refresh);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            reload();
        }
    }

    /**
     * 成员名用正常色，后面跟的注释用灰色。拼成一个字符串就没法分开上色了，所以
     * {@link MemberNode} 把两段分开存
     */
    private static class MemberCellRenderer extends ColoredTreeCellRenderer {

        @Override
        public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected,
                                          boolean expanded, boolean leaf, int row, boolean hasFocus) {
            if (!(value instanceof MemberNode)) {
                //提示行（没打开文件、成员太多）整行灰字
                append(String.valueOf(value), SimpleTextAttributes.GRAYED_ATTRIBUTES);
                return;
            }
            final MemberNode node = (MemberNode) value;
            setIcon(node.getIcon());
            append(node.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
            if (!node.getComment().isEmpty()) {
                append("  " + node.getComment(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
            }
        }
    }
}