package com.plugins.infotip.storage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把 6.x / 5.x 的平铺配置转成 V7 的嵌套结构
 *
 * <p>
 * 只做文本：读老文件是 {@link LegacyReader} 的事，落盘是 {@link XmlFileUtils} 的事，这里拿
 * 一串完整路径的规则，吐出一整个 {@code DirectoryV7.xml} 的正文。中间不碰平台 API，
 * 要验证可以照 CLAUDE.md 说的抄成单文件 Java 跑断言。
 * </p>
 *
 * <h3>为什么重排之后命中结果不变</h3>
 * <p>
 * 嵌套是按路径归堆，文件里的先后顺序整个被打乱了，而 {@code TreesUtils.getMatchPath} 里
 * 「同路径先到先得」是认顺序的。之所以还是安全的，是因为<b>只有完整路径相同的规则才会互相竞争</b>
 * （全等才命中），而它们归堆之后都落在同一个节点里，同一个节点里的相对顺序这里原样保留。
 * 跨路径的规则本来就不靠顺序分胜负。
 * </p>
 */
public class V7Migrator {

    /**
     * 缩进一层的宽度
     */
    private static final String INDENT = "  ";

    private V7Migrator() {
    }

    /**
     * 生成整个 V7 文件的正文
     *
     * @param entities 老文件里的全部规则，路径必须已经展开成完整路径
     * @param header   {@code <trees>} 之前的部分（XML 声明和那行提示）
     * @param footer   {@code </trees>} 之后的那段参数说明注释
     * @return 可以直接写盘的文本，行分隔符统一用 {@code \r\n}，和 {@code XML_TEMPLATE} 一致
     */
    public static String buildDocument(List<XmlEntity> entities, String header, String footer) {
        final Node root = new Node("");
        for (XmlEntity entity : entities) {
            if (null == entity) {
                continue;
            }
            Node cursor = root;
            for (String segment : NodePaths.segments(entity.getPath())) {
                cursor = cursor.child(segment);
            }
            cursor.rules.add(entity);
        }
        final List<String> lines = new ArrayList<>();
        lines.add("<trees>");
        //根节点自己的规则：路径为空（写成 path="/" 或省略）的规则挂在这里，直接铺在 <trees> 下
        for (XmlEntity rule : root.rules) {
            lines.add(INDENT + selfClosing(rule, null));
        }
        renderChildren(lines, root, 1);
        lines.add("</trees>");
        return header + String.join("\r\n", lines) + footer;
    }

    /**
     * 递归输出一个节点的全部子节点
     *
     * <p>
     * 沿路把<b>单链压平</b>：{@code /src/main/java} 底下才有规则时，写成一个
     * {@code <node path="src/main/java">} 而不是三层空壳。判据是「自己没有规则、而且只有一个孩子」，
     * 有规则的节点绝不能被并掉——那条规则是挂在这一层的路径上的。
     * </p>
     */
    private static void renderChildren(List<String> lines, Node parent, int depth) {
        final String indent = indent(depth);
        for (Node child : parent.children.values()) {
            String segment = child.segment;
            Node node = child;
            while (node.rules.isEmpty() && 1 == node.children.size()) {
                final Node only = node.children.values().iterator().next();
                segment = segment + "/" + only.segment;
                node = only;
            }
            //这一层的第一条规则直接写在节点自己身上，剩下的同路径规则（重复项）
            //作为不带 path 的子节点跟在后面，相对顺序保持不变
            final XmlEntity own = node.rules.isEmpty() ? null : node.rules.get(0);
            final List<XmlEntity> rest = new ArrayList<>(node.rules);
            rest.remove(own);
            if (rest.isEmpty() && node.children.isEmpty()) {
                lines.add(indent + selfClosing(own, segment));
                continue;
            }
            lines.add(indent + openTag(own, segment));
            for (XmlEntity rule : rest) {
                lines.add(indent(depth + 1) + selfClosing(rule, null));
            }
            renderChildren(lines, node, depth + 1);
            lines.add(indent + "</" + XmlStorage.NODE + ">");
        }
    }

    private static String selfClosing(XmlEntity rule, String segment) {
        return "<" + XmlStorage.NODE + attributes(rule, segment) + "/>";
    }

    private static String openTag(XmlEntity rule, String segment) {
        return "<" + XmlStorage.NODE + attributes(rule, segment) + ">";
    }

    /**
     * 属性串，顺序固定，空值一律不写
     *
     * @param rule    这个标签承载的规则，纯容器节点传 null
     * @param segment 相对上一层的那截路径，不写 path 时传 null
     */
    private static String attributes(XmlEntity rule, String segment) {
        final StringBuilder sb = new StringBuilder();
        append(sb, XmlStorage.PATH, segment);
        if (null != rule) {
            append(sb, XmlStorage.NOTE, rule.getNote());
            append(sb, XmlStorage.LABEL, rule.getLabel());
            append(sb, XmlStorage.TOOLTIP, rule.getTooltip());
            append(sb, XmlStorage.ICON, rule.getIcon());
            append(sb, XmlStorage.COLOR, rule.getColor());
            append(sb, XmlStorage.BG, rule.getBg());
            append(sb, XmlStorage.STRIKE, rule.isStrikeEnabled() ? "true" : null);
        }
        return sb.toString();
    }

    private static void append(StringBuilder sb, String name, String value) {
        if (isBlank(value)) {
            return;
        }
        sb.append(' ').append(name).append("=\"").append(escape(value.trim())).append('"');
    }

    /**
     * 转义属性值
     *
     * <p>
     * 换行必须写成 {@code &#10;}：XML 规范要求解析器把属性值里的裸换行归一成空格，
     * 悬浮提示是可以多行的，直接写进去再读出来就少了换行。制表符同理。
     * </p>
     */
    private static String escape(String value) {
        final StringBuilder sb = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            switch (c) {
                case '&':
                    sb.append("&amp;");
                    break;
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                case '"':
                    sb.append("&quot;");
                    break;
                case '\n':
                    sb.append("&#10;");
                    break;
                case '\r':
                    sb.append("&#13;");
                    break;
                case '\t':
                    sb.append("&#9;");
                    break;
                default:
                    sb.append(c);
                    break;
            }
        }
        return sb.toString();
    }

    private static String indent(int depth) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            sb.append(INDENT);
        }
        return sb.toString();
    }

    private static boolean isBlank(String value) {
        return null == value || value.trim().isEmpty();
    }

    /**
     * 建树用的中间节点，一个节点对应一段目录名
     */
    private static class Node {

        private final String segment;

        /**
         * 用 {@link LinkedHashMap} 而不是普通 map：同一层子目录的先后要跟着老文件里第一次出现的
         * 顺序走，不然每次迁移生成的文件都不一样
         */
        private final Map<String, Node> children = new LinkedHashMap<>();

        /**
         * 挂在这个路径上的规则，可能不止一条（一条路径规则 + 若干条限定在这个目录的类型规则）
         */
        private final List<XmlEntity> rules = new ArrayList<>();

        Node(String segment) {
            this.segment = segment;
        }

        Node child(String segment) {
            return children.computeIfAbsent(segment, Node::new);
        }
    }
}
