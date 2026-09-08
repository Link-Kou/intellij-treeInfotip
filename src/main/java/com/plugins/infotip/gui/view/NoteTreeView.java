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
import com.plugins.infotip.PluginStartupActivity;
import com.plugins.infotip.gui.IconsUtils;
import com.plugins.infotip.gui.compone.MyTreeNode;
import com.plugins.infotip.storage.XmlEntity;
import com.plugins.infotip.storage.XmlFileUtils;
import com.plugins.infotip.storage.XmlStorage;
import com.plugins.infotip.trees.TreesUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A <code>NoteTreeView</code> Class
 * <p>
 * 「目录备注」列表：{@code DirectoryV3.xml} 里配的规则平铺一行一条。
 * </p>
 * <p>
 * 是平铺而不是真实的目录树——规则是稀疏的，一条 {@code /src/main/java/a/b/C.java} 在树里要
 * 建五层空目录才够挂上它，翻起来比一行一条还慢。既然不做树，5.4.1 之前那个「备注列表（双击刷新）」
 * 的根节点也就没用了：刷新挪到了工具栏上，列表直接从第一行开始。
 * </p>
 * <p>
 * 路径已失效的规则排在最前面。项目树上已经没有它们的节点了，这个列表是用户唯一能发现并清掉
 * 它们的地方，所以还给了工具栏上的「清除失效路径」一键删。
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

    private NoteTreeView(@NotNull Project project) {
        super(new DefaultMutableTreeNode("备注列表"));
        this.project = project;
        //不做树，根节点就没有存在的意义了；也不留展开箭头的缩进
        setRootVisible(false);
        setShowsRootHandles(false);
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

    private JComponent createToolbar() {
        final DefaultActionGroup group = new DefaultActionGroup();
        group.add(new RefreshAction());
        group.add(new ClearMissingAction());
        group.add(new ClearShadowedAction());
        final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(PLACE_TOOLBAR, group, true);
        //不设 targetComponent 平台会警告，action 的 update 也拿不到正确的 DataContext
        toolbar.setTargetComponent(this);
        return toolbar.getComponent();
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

    private void installNavigation() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (2 != e.getClickCount()) {
                    return;
                }
                final TreePath path = getPathForLocation(e.getX(), e.getY());
                if (null == path) {
                    return;
                }
                final Object component = path.getLastPathComponent();
                if (!(component instanceof MyTreeNode)) {
                    return;
                }
                final MyTreeNode node = (MyTreeNode) component;
                final Object obj = node.getUserEntity();
                if (obj instanceof XmlEntity) {
                    navigate(project, (XmlEntity) obj, node.isShadowed());
                }
            }
        });
    }

    /**
     * 重建整个列表，路径已失效的排在最前面
     * <p>
     * 失效的必须让用户一眼看见：项目树上已经没有它们的节点了，不排上去就得自己往下翻。
     * </p>
     * <p>
     * 顺手按 {@link TreesUtils#ruleKey} 查重：键相同的第二条及以后的规则被前面那条完全盖住，
     * 一辈子不会生效（三类规则都是先到先得），标灰显示并攒进 {@link #shadowedEntities}。
     * 只标不删——文件是用户手改的，静默重写不合适。
     * </p>
     */
    private void reload() {
        final DefaultTreeModel model = (DefaultTreeModel) getModel();
        final DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
        root.removeAllChildren();
        missingEntities.clear();
        shadowedEntities.clear();
        final List<MyTreeNode> missing = new ArrayList<>();
        final List<MyTreeNode> alive = new ArrayList<>();
        final List<XmlEntity> entities = XmlStorage.getXmlEntity(project);
        if (null != entities) {
            final Set<String> seen = new HashSet<>();
            for (XmlEntity entity : entities) {
                //键要在建节点之前登记：没有文字的规则不进列表，但它照样占着这条键，
                //后面同键的那条确实不生效
                final boolean shadowed = !seen.add(TreesUtils.ruleKey(entity));
                if (shadowed) {
                    shadowedEntities.add(entity);
                }
                final MyTreeNode node = buildNode(project, entity, shadowed);
                //没有文字可显示、路径又还在、也没被盖住的规则不进列表，见 buildNode
                if (null == node) {
                    continue;
                }
                if (node.isMissing()) {
                    missing.add(node);
                    missingEntities.add(entity);
                } else {
                    alive.add(node);
                }
            }
        }
        for (MyTreeNode node : missing) {
            root.add(node);
        }
        for (MyTreeNode node : alive) {
            root.add(node);
        }
        //reload 要放在加完子节点之后：root.add 走的是 DefaultMutableTreeNode 自己的方法，
        //不发 model 事件，先 reload 再 add 的话新节点得等下一次重绘才出得来
        model.reload();
        //rootVisible=false 的树，根节点自己也要展开，否则一行都看不到
        expandPath(new TreePath(root));
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
     * 双击一条备注：能落到真实文件就跳文件，否则跳到 {@code DirectoryV3.xml} 里这条规则所在的行
     * <p>
     * 跳 XML 覆盖三种双击没反应的情况：路径已经被删或改名的（列表里标红那些）、
     * 被前面同键规则覆盖而不生效的（列表里标灰加删除线那些），
     * 以及只写了 extension 的全项目类型规则（本来就没有路径可跳）。
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
     * 打开 {@code DirectoryV3.xml} 并把光标放到这条 {@code <tree>} 标签上
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
     * 建一个节点，同时把图标、路径失效和被覆盖状态算好存进去
     * <p>
     * 图标表示这条规则作用在什么上（目录 / 哪类文件 / 路径已失效），不是用户自己配的那个图标——
     * 配的图标在项目树上已经看得到，摆这里反而会盖掉目录和文件的区分。
     * </p>
     * <p>
     * 存不存在只在这里查一次，不放渲染器里：渲染器每帧对每个可见行都要调一次，不能碰 VFS。
     * </p>
     *
     * @param shadowed 前面已经有一条同键的规则了，这条不会生效，见 {@link TreesUtils#ruleKey}
     * @return 没有任何文字可显示、路径又还在、也没被盖住的规则返回 {@code null}，调用方跳过不加进列表
     */
    private static MyTreeNode buildNode(Project project, XmlEntity entity, boolean shadowed) {
        final String extension = entity.getExtension();
        final boolean typeRule = !trimmed(extension).isEmpty();
        final boolean scoped = !trimmed(entity.getPath()).isEmpty();
        //只写 extension 的全项目规则没有路径可查，永远算有效；
        //其余的（含既没路径也没扩展名的空规则）查不到文件就是失效
        final boolean projectWide = typeRule && !scoped;
        final VirtualFile file = projectWide ? null : TreesUtils.findProjectFile(project, entity.getPath());
        final boolean missing = !projectWide && null == file;
        //被盖住的和失效的一样：不显示就等于用户在哪都发现不了它，所以没写文字时拿路径兜底
        final String label = label(entity, typeRule, missing || shadowed);
        if (label.isEmpty()) {
            return null;
        }
        final MyTreeNode node = new MyTreeNode(label).setUserEntity(entity).setShadowed(shadowed);
        if (missing) {
            return node.setMissing(true).setIcon(fit(AllIcons.General.Error));
        }
        //类型规则的 path 是限定目录，图标按它管的那类文件给
        if (typeRule) {
            return node.setIcon(extensionIcon(extension));
        }
        if (file.isDirectory()) {
            return node.setIcon(fit(AllIcons.Nodes.Folder));
        }
        return node.setIcon(fit(FileTypeManager.getInstance().getFileTypeByFileName(file.getName()).getIcon()));
    }

    /**
     * 扩展名对应的文件类型图标，认不出来的扩展名会落到 UnknownFileType 的图标
     */
    private static Icon extensionIcon(String extension) {
        return fit(FileTypeManager.getInstance().getFileTypeByExtension(extension.trim()).getIcon());
    }

    /**
     * 统一缩到 16。{@code AllIcons} 里有 32×15、18×22 这种，不缩会把列表行高撑起来
     */
    private static Icon fit(Icon icon) {
        return null == icon ? AllIcons.FileTypes.Any_type : IconsUtils.fit(icon);
    }

    /**
     * 列表里显示的文字，返回空串表示这条规则整个不进列表
     * <p>
     * 用户写的东西优先：备注（title）→ 覆盖的显示名（presentableText）。类型规则再补上
     * 「*.扩展名 @ 生效范围」，不然看不出它管的是哪一批文件。
     * </p>
     * <p>
     * 两个都没写的规则（只设了颜色 / 图标 / 删除线的那些）分两种情况：
     * </p>
     * <ul>
     *   <li><b>看得见效果的不进列表</b>：效果在项目树上本来就看得见，列表里却只有空白一行，
     *   认不出是哪条也点不动，而它的入口就在项目树的右键菜单上。</li>
     *   <li><b>路径失效或被覆盖的必须进列表</b>：前者在项目树上连节点都没有了，后者压根不生效，
     *   列表是用户唯一能发现并清掉它的地方。没有文字可显示，就拿它配的路径当标题。</li>
     * </ul>
     *
     * @param forceShow 这条规则在项目树上看不到效果（路径失效或被前面同键的规则盖住），
     *                  没写文字也得显示出来
     */
    private static String label(XmlEntity entity, boolean typeRule, boolean forceShow) {
        String text = trimmed(entity.getTitle());
        if (text.isEmpty()) {
            text = trimmed(entity.getPresentableText());
        }
        if (typeRule) {
            final String scope = trimmed(entity.getPath());
            final String suffix = "*." + trimmed(entity.getExtension()) + " @ " + (scope.isEmpty() ? "整个项目" : scope);
            if (!text.isEmpty()) {
                return text + "  [" + suffix + "]";
            }
            return forceShow ? suffix : "";
        }
        if (!text.isEmpty()) {
            return text;
        }
        return forceShow ? trimmed(entity.getPath()) : "";
    }

    private static String trimmed(String value) {
        return null == value ? "" : value.trim();
    }

    /**
     * 删除是不可逆的（改的是用户项目里的 {@code DirectoryV3.xml}），所以两个删除动作都要先问一句
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
            super("清除失效路径", "删掉所有路径已经不存在的规则", AllIcons.Actions.GC);
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
     * 之所以要用户按一下、不在解析时自动去重：{@code DirectoryV3.xml} 躺在用户项目根目录里，
     * 是可以手改也会进版本库的文件，插件不该背着人重写它。
     * </p>
     */
    private class ClearShadowedAction extends AnAction {

        ClearShadowedAction() {
            super("清理重复规则", "删掉被前面同路径规则盖住、永远不会生效的那些规则", AllIcons.Actions.Copy);
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
            super("删除", "从 DirectoryV3.xml 里删掉选中的规则", AllIcons.General.Remove);
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
     * 右键菜单里的「置顶」，把选中的 {@code <tree>} 挪到 {@code DirectoryV3.xml} 的最前面
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
                append("  路径已失效（双击定位到 XML）", SimpleTextAttributes.GRAYED_ATTRIBUTES);
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
                append(text, SimpleTextAttributes.REGULAR_ATTRIBUTES);
            }
        }
    }
}
