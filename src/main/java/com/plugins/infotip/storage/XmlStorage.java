package com.plugins.infotip.storage;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 读写 {@code DirectoryV7.xml}
 *
 * <p>
 * 7.0.0 起配置是<b>嵌套</b>的：一个 {@code <node>} 可以套下一层 {@code <node>}，每层的
 * {@code path} 只写相对上一层的那一截。重复的长目录因此天生只写一遍，6.x 那套
 * {@code prefix} 前缀表连同「抽离或还原路径前缀」按钮一并去掉了。
 * </p>
 * <p>
 * <b>内存里存的一律是完整路径</b>（{@link XmlEntity#getPath()}），匹配、右键菜单、侧边栏
 * 那些地方完全不知道嵌套这回事。老文件的读取和转换见 {@link LegacyReader} 和 {@link V7Migrator}。
 * </p>
 */
public class XmlStorage {

    //region 节点与属性名
    final static String TREES = "trees";

    /**
     * 唯一的节点标签名。6.x 叫 {@code tree}，而且只有一层
     */
    final static String NODE = "node";

    final static String PATH = "path";

    /**
     * 备注文字。6.x 叫 {@code title}
     */
    final static String NOTE = "note";

    /**
     * 覆盖显示名。6.x 叫 {@code presentableText}
     */
    final static String LABEL = "label";

    /**
     * 悬浮提示。6.x 叫 {@code tooltipTitle}
     */
    final static String TOOLTIP = "tooltip";

    final static String ICON = "icon";

    /**
     * 文字颜色。6.x 叫 {@code textColor}
     */
    final static String COLOR = "color";

    /**
     * 背景色。6.x 叫 {@code backgroundColor}
     */
    final static String BG = "bg";

    /**
     * 删除线。6.x 叫 {@code strikethrough}
     */
    final static String STRIKE = "strike";

    /**
     * 除 {@code path} 之外的全部属性：{@code path} 是节点在树里的位置，其余才是「配了什么」。
     * 判断一个节点是不是纯容器、以及清空一条规则时都按这张表来，<b>新增可配置属性必须往这里加一项</b>
     */
    final static String[] SETTING_ATTRIBUTES = {NOTE, LABEL, TOOLTIP, ICON, COLOR, BG, STRIKE};
    //endregion

    private final static ConcurrentHashMap<Project, CopyOnWriteArrayList<XmlEntity>> XML_STORAGE_LIST = new ConcurrentHashMap<>();

    /**
     * 解析整个配置文件
     *
     * <p>
     * 深度优先、先序遍历，一路把每层的 {@code path} 累加成完整路径。<b>先序</b>是必须的：
     * 同优先级时靠前的规则赢（见 {@code TreesUtils.getMatchPath}），列表顺序要和文件里读起来的
     * 顺序一致，用户才能靠「置顶」控制谁生效。
     * </p>
     *
     * @param project 项目
     * @param xmlFile 配置文件
     */
    public static synchronized void parsing(Project project, XmlFile xmlFile) {
        if (null == xmlFile) {
            return;
        }
        XML_STORAGE_LIST.remove(project);
        final CopyOnWriteArrayList<XmlEntity> entities = new CopyOnWriteArrayList<>();
        XML_STORAGE_LIST.put(project, entities);
        if (null == project.getPresentableUrl()) {
            return;
        }
        final XmlTag rootTag = rootTag(xmlFile);
        if (null == rootTag) {
            return;
        }
        collect(rootTag, "", entities);
    }

    /**
     * 递归收一层
     *
     * @param parent     当前这层的父标签，最外层传 {@code <trees>}
     * @param parentPath 父标签对应的完整路径，最外层是空串
     */
    private static void collect(XmlTag parent, String parentPath, List<XmlEntity> out) {
        for (XmlTag tag : parent.findSubTags(NODE)) {
            final String fullPath = NodePaths.join(parentPath, tag.getAttributeValue(PATH));
            final XmlEntity entity = toEntity(tag, fullPath);
            //纯容器节点（只有 path、什么都没配）不进列表：它不是规则，进去会让侧边栏的查重
            //把同路径的真规则判成「被盖住、不生效」
            if (entity.hasAnySetting()) {
                out.add(entity);
            }
            collect(tag, fullPath, out);
        }
    }

    private static XmlEntity toEntity(XmlTag tag, String fullPath) {
        return new XmlEntity()
                .setPath(fullPath)
                .setNote(value(tag, NOTE))
                .setLabel(value(tag, LABEL))
                .setTooltip(value(tag, TOOLTIP))
                .setIcon(value(tag, ICON))
                .setColor(value(tag, COLOR))
                .setBg(value(tag, BG))
                .setStrike(tag.getAttributeValue(STRIKE))
                .setTag(tag);
    }

    /**
     * 缺失的属性归一成空串，调用方就不用到处判 null
     */
    private static String value(XmlTag tag, String name) {
        final String result = tag.getAttributeValue(name);
        return null == result ? "" : result;
    }

    static XmlTag rootTag(XmlFile xmlFile) {
        if (null == xmlFile) {
            return null;
        }
        final XmlDocument document = xmlFile.getDocument();
        if (null == document) {
            return null;
        }
        final XmlTag rootTag = document.getRootTag();
        return null != rootTag && TREES.equals(rootTag.getName()) ? rootTag : null;
    }

    public static void clear(Project project) {
        final List<XmlEntity> entities = XML_STORAGE_LIST.get(project);
        if (null != entities) {
            entities.clear();
        }
    }

    public static List<XmlEntity> getXmlEntity(Project project) {
        return XML_STORAGE_LIST.get(project);
    }

    /**
     * 把整棵树上的 {@code <node>} 连同它的完整路径全收出来，写操作用它定位标签
     */
    private static List<XmlEntity> flatten(XmlFile xmlFile) {
        final List<XmlEntity> all = new ArrayList<>();
        final XmlTag rootTag = rootTag(xmlFile);
        if (null != rootTag) {
            collectAll(rootTag, "", all);
        }
        return all;
    }

    private static void collectAll(XmlTag parent, String parentPath, List<XmlEntity> out) {
        for (XmlTag tag : parent.findSubTags(NODE)) {
            final String fullPath = NodePaths.join(parentPath, tag.getAttributeValue(PATH));
            out.add(toEntity(tag, fullPath));
            collectAll(tag, fullPath, out);
        }
    }

    /**
     * 写一条规则的全部属性
     */
    private static void writeSettings(XmlTag tag, XmlEntity entity) {
        NodeWriter.setIfNotEmpty(tag, NOTE, entity.getNote());
        NodeWriter.setIfNotEmpty(tag, LABEL, entity.getLabel());
        NodeWriter.setIfNotEmpty(tag, TOOLTIP, entity.getTooltip());
        NodeWriter.setIfNotEmpty(tag, ICON, entity.getIcon());
        NodeWriter.setIfNotEmpty(tag, COLOR, entity.getColor());
        NodeWriter.setIfNotEmpty(tag, BG, entity.getBg());
        NodeWriter.setIfNotEmpty(tag, STRIKE, entity.isStrikeEnabled() ? "true" : null);
    }

    /**
     * 新建一条规则
     *
     * <p>
     * 沿着完整路径把缺的层一层层建出来，规则就落在那个路径对应的节点上。根目录（{@code <trees>}
     * 不是 {@code <node>}）挂不了属性，这种情况另起一个子节点。
     * </p>
     *
     * @param project   项目
     * @param xmlFile   配置文件
     * @param xmlEntity 规则，{@code path} 必须是完整路径
     */
    public static synchronized void create(Project project, XmlFile xmlFile, XmlEntity xmlEntity) {
        final XmlTag rootTag = rootTag(xmlFile);
        if (null == rootTag || null == xmlEntity) {
            return;
        }
        WriteCommandAction.runWriteCommandAction(project, () -> {
            final XmlTag holder = NodeWriter.ensurePath(rootTag, xmlEntity.getPath());
            final XmlTag target;
            if (holder == rootTag) {
                //路径为空、落到了根：<trees> 上挂不了属性，另起一个子节点
                final XmlTag child = holder.createChildTag(NODE, holder.getNamespace(), null, false);
                writeSettings(child, xmlEntity);
                target = holder.addSubTag(child, false);
            } else {
                target = holder;
                writeSettings(target, xmlEntity);
            }
            xmlEntity.setTag(target);
            XmlFileUtils.saveFileXml(project);
        });
    }

    /**
     * 改一条已有规则
     *
     * <p>
     * <b>只写属性，不动 {@code path}</b>：调用方拿到的都是解析出来的实体，改的是备注、颜色这些，
     * 没有谁会去改路径。真要换路径是删一条加一条，不是原地改。
     * </p>
     */
    public static synchronized void modify(Project project, XmlFile fileDirectoryXml, XmlEntity xmlEntity) {
        if (null == xmlEntity) {
            return;
        }
        final XmlTag tag = xmlEntity.getTag();
        if (null == tag || !tag.isValid()) {
            create(project, fileDirectoryXml, xmlEntity);
            return;
        }
        WriteCommandAction.runWriteCommandAction(project, () -> {
            writeSettings(tag, xmlEntity);
            XmlFileUtils.saveFileXml(project);
        });
    }

    /**
     * 按完整路径删规则
     *
     * <p>
     * 同一条路径上的规则可能不止一条（用户手改时复制粘贴出来的），一次全删掉。
     * </p>
     */
    public static synchronized void remove(XmlFile xmlFile, Project project, XmlEntity xmlEntity) {
        if (null == xmlEntity) {
            return;
        }
        final List<XmlEntity> targets = new ArrayList<>();
        for (XmlEntity candidate : flatten(xmlFile)) {
            if (NodePaths.trimTrailingSlash(xmlEntity.getPath()).equals(NodePaths.trimTrailingSlash(candidate.getPath()))
                    && candidate.hasAnySetting()) {
                targets.add(candidate);
            }
        }
        removeByTag(project, targets);
    }

    /**
     * 直接按 {@link XmlEntity#getTag()} 删若干条规则，一个写操作删完
     *
     * <p>
     * 标签底下还挂着子节点时只摘掉它自己的属性、留着当容器；删空之后一路往上清掉空壳，
     * 见 {@link NodeWriter#deleteRule}。
     * </p>
     *
     * @return 实际删掉的条数；标签已失效（文件被外部改过、还没重新解析）的会被跳过
     */
    public static synchronized int removeByTag(Project project, List<XmlEntity> entities) {
        if (null == entities || entities.isEmpty()) {
            return 0;
        }
        final int[] removed = {0};
        WriteCommandAction.runWriteCommandAction(project, () -> {
            for (XmlEntity entity : entities) {
                if (null == entity) {
                    continue;
                }
                final XmlTag tag = entity.getTag();
                if (null != tag && tag.isValid()) {
                    NodeWriter.deleteRule(tag);
                    removed[0]++;
                }
            }
            if (removed[0] > 0) {
                XmlFileUtils.saveFileXml(project);
            }
        });
        return removed[0];
    }

    /**
     * 置顶：把这些规则挪到<b>各自父节点</b>的最前面
     *
     * <p>
     * 6.x 是平铺的，置顶就得挪到整个文件的最前面。嵌套之后不用了：只有<b>路径相同</b>的规则才会
     * 靠先后顺序分胜负（路径规则要全等才命中，目录级类型规则先比 path 长度），而路径相同的规则
     * 归堆之后就是同一个父节点下的兄弟。挪到兄弟里的第一个，就已经赢了所有会和它竞争的规则。
     * </p>
     * <p>
     * 顺带避开了 6.x 的一个坑：那时要往上层挪，一条规则的移动会让另一条身上存的标签失效。
     * 现在只在兄弟之间动，各动各的互不影响。
     * </p>
     * <p>
     * PSI 没有「移动子节点」，只能先在头部插一份副本再删原件，两步不能颠倒。多条一起置顶时要
     * <b>倒着遍历</b>，正着走会把这批规则整体翻个面。而且必须在<b>同一个写操作</b>里做完——
     * 每条各开一次写操作会各触发一次存盘和重新解析，后面那些标签就都失效了。
     * </p>
     *
     * @return 实际挪动的条数
     */
    public static synchronized int moveToTop(Project project, List<XmlEntity> entities) {
        if (null == entities || entities.isEmpty()) {
            return 0;
        }
        final int[] moved = {0};
        WriteCommandAction.runWriteCommandAction(project, () -> {
            if (alreadyOnTop(entities)) {
                return;
            }
            for (int i = entities.size() - 1; i >= 0; i--) {
                final XmlEntity entity = entities.get(i);
                if (null == entity) {
                    continue;
                }
                final XmlTag tag = entity.getTag();
                if (null == tag || !tag.isValid()) {
                    continue;
                }
                final XmlTag parent = tag.getParentTag();
                if (null == parent) {
                    continue;
                }
                final XmlTag[] siblings = parent.getSubTags();
                //已经是头一个就不动，省一次写和一次重新解析
                if (siblings.length > 0 && siblings[0] == tag) {
                    continue;
                }
                entity.setTag(parent.addSubTag(tag, true));
                tag.delete();
                moved[0]++;
            }
            if (moved[0] > 0) {
                XmlFileUtils.saveFileXml(project);
            }
        });
        return moved[0];
    }

    /**
     * 这批规则是不是已经各自贴在父节点最前面，而且顺序就是给进来的这个
     *
     * <p>
     * 不先查一下的话，对一批本来就在最前面的规则点「置顶」会白写一次文件：倒着遍历时
     * 第一个处理的反而不在头位，会被挪走，接着后面的又把它顶回去，结果一样但文件已经改过了。
     * 这文件躺在用户项目根目录、会进版本库，不能凭空多出一次改动。
     * </p>
     */
    private static boolean alreadyOnTop(List<XmlEntity> entities) {
        final java.util.Map<XmlTag, Integer> taken = new java.util.HashMap<>();
        for (XmlEntity entity : entities) {
            if (null == entity) {
                continue;
            }
            final XmlTag tag = entity.getTag();
            if (null == tag || !tag.isValid()) {
                continue;
            }
            final XmlTag parent = tag.getParentTag();
            if (null == parent) {
                return false;
            }
            //同一个父节点下的第 n 条，就该正好站在第 n 个位置上
            final int expected = taken.merge(parent, 1, Integer::sum) - 1;
            final XmlTag[] siblings = parent.getSubTags();
            if (expected >= siblings.length || siblings[expected] != tag) {
                return false;
            }
        }
        return true;
    }
}
