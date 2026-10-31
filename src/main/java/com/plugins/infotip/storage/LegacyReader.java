package com.plugins.infotip.storage;

import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 读 6.x / 5.x 的平铺配置文件
 *
 * <p>
 * 只在迁移时用一次：{@link V7Migrator} 拿它读出老文件的全部规则，转成 V7 的嵌套结构。
 * 运行时的解析走 {@link XmlStorage#parsing}，不经过这里。
 * </p>
 * <p>
 * 老格式的两件事在这里一次性消化掉，之后全项目就没人知道它们了：
 * </p>
 * <ul>
 *   <li><b>{@code prefix} 前缀表</b>——6.0.0 引入，作用是让重复的长目录只写一遍。V7 的嵌套
 *   天生就做到了这件事，所以整套前缀机制（{@code PathPrefixes} 那几个类和「抽离或还原路径前缀」
 *   按钮）7.0.0 一并去掉了。但<b>读老文件时必须照样展开</b>：不展开的话抽离过的文件里
 *   {@code path} 只是半截相对路径，迁出来的规则会全指到错的地方。</li>
 *   <li><b>老属性名</b>——{@code title} / {@code presentableText} / {@code tooltipTitle} /
 *   {@code textColor} / {@code backgroundColor} / {@code strikethrough}，在这里映射成
 *   {@link XmlEntity} 的新字段名。</li>
 * </ul>
 */
public class LegacyReader {

    //region 老格式的节点与属性名
    private static final String TREES = "trees";

    private static final String TREE = "tree";

    private static final String PATH = "path";

    private static final String EXTENSION = "extension";

    private static final String TITLE = "title";

    private static final String PRESENTABLE_TEXT = "presentableText";

    private static final String TOOLTIP_TITLE = "tooltipTitle";

    private static final String ICON = "icon";

    private static final String TEXT_COLOR = "textColor";

    private static final String BACKGROUND_COLOR = "backgroundColor";

    private static final String STRIKETHROUGH = "strikethrough";

    private static final String PREFIXES = "prefixes";

    private static final String PREFIX = "prefix";

    private static final String PREFIX_ID = "id";
    //endregion

    private LegacyReader() {
    }

    /**
     * 把一个平铺的老文件读成规则列表
     *
     * <p>
     * 顺序就是文件里的顺序，一条都不合并、不去重：同优先级时靠前的赢，迁移必须原样保留这个次序，
     * 否则用户看到的效果会变。重复规则的清理是侧边栏「清理重复规则」的事，不在迁移时代劳。
     * </p>
     *
     * @param xmlFile 老的 {@code DirectoryV6.xml} 或 {@code DirectoryV3.xml}
     * @return 路径一律是展开后的完整路径；文件结构不对时返回空列表
     */
    public static List<XmlEntity> read(XmlFile xmlFile) {
        final List<XmlEntity> entities = new ArrayList<>();
        if (null == xmlFile) {
            return entities;
        }
        final XmlDocument document = xmlFile.getDocument();
        if (null == document) {
            return entities;
        }
        final XmlTag rootTag = document.getRootTag();
        if (null == rootTag || !TREES.equals(rootTag.getName())) {
            return entities;
        }
        final Map<String, String> prefixes = readPrefixes(rootTag);
        //不用递归访问器：老格式里 <tree> 全是 <trees> 的直接子节点，直接取一层就够，
        //顺序也更可控
        for (XmlTag tag : rootTag.findSubTags(TREE)) {
            final XmlEntity entity = tree(tag, prefixes);
            if (null != entity) {
                entities.add(entity);
            }
        }
        return entities;
    }

    /**
     * 收前缀表：id 到完整路径
     *
     * <p>
     * 同一个 id 声明了两次时先到先得。没有 {@code <prefixes>} 段（没抽离过的文件、以及 V3）
     * 就是空表，后面按「只有 path」处理。
     * </p>
     */
    private static Map<String, String> readPrefixes(XmlTag rootTag) {
        final Map<String, String> prefixes = new LinkedHashMap<>();
        for (XmlTag holder : rootTag.findSubTags(PREFIXES)) {
            for (XmlTag prefix : holder.findSubTags(PREFIX)) {
                final String id = trimToEmpty(prefix.getAttributeValue(PREFIX_ID));
                final String path = trimToEmpty(prefix.getAttributeValue(PATH));
                if (!id.isEmpty() && !path.isEmpty() && !prefixes.containsKey(id)) {
                    prefixes.put(id, path);
                }
            }
        }
        return prefixes;
    }

    /**
     * 解析一条 {@code <tree>}
     *
     * <p>
     * 带 {@code extension} 的老「类型规则」<b>直接丢弃</b>：7.1.0 去掉了 extension 这个属性，
     * 一条规则只对应一个路径，类型规则没有对等的迁法。没有 path、只有 extension 的那些迁过来
     * 也不会命中任何东西，丢掉最干净。
     * </p>
     *
     * @return 没有可迁内容、或本就是类型规则的返回 null
     */
    private static XmlEntity tree(XmlTag tag, Map<String, String> prefixes) {
        final String rawPath = tag.getAttributeValue(PATH);
        final String extension = tag.getAttributeValue(EXTENSION);
        final String prefixRef = tag.getAttributeValue(PREFIX);
        //类型规则（带 extension）不再支持，跳过
        if (null != extension && !extension.trim().isEmpty()) {
            return null;
        }
        //没有任何路径信息的标签也没法迁
        if (null == rawPath && null == prefixRef) {
            return null;
        }
        return new XmlEntity()
                .setPath(expandPath(prefixRef, rawPath, prefixes))
                .setNote(tag.getAttributeValue(TITLE))
                .setLabel(tag.getAttributeValue(PRESENTABLE_TEXT))
                .setTooltip(tag.getAttributeValue(TOOLTIP_TITLE))
                .setIcon(tag.getAttributeValue(ICON))
                .setColor(tag.getAttributeValue(TEXT_COLOR))
                .setBg(tag.getAttributeValue(BACKGROUND_COLOR))
                .setStrike(tag.getAttributeValue(STRIKETHROUGH));
    }

    /**
     * 把 {@code prefix} + {@code path} 拼成完整路径
     *
     * <p>
     * 引用了一个查不到的 id 时按「只有 path」处理，不把这条规则丢掉：文件是用户手改的，
     * 拼错 id 也该让这条规则跟着迁过去，而不是凭空消失。
     * </p>
     */
    private static String expandPath(String prefixRef, String rawPath, Map<String, String> prefixes) {
        if (null == prefixRef) {
            return rawPath;
        }
        final String base = prefixes.get(trimToEmpty(prefixRef));
        if (null == base) {
            return rawPath;
        }
        return null == rawPath ? base : base + rawPath;
    }

    private static String trimToEmpty(String value) {
        return null == value ? "" : value.trim();
    }
}
