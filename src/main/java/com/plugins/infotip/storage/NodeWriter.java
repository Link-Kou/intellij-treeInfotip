package com.plugins.infotip.storage;

import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.xml.XmlTag;

import java.util.List;

/**
 * 在嵌套结构上定位、新建、清理 {@code <node>} 标签
 *
 * <p>
 * {@link XmlStorage} 的写操作全靠这里定位到「该改哪个标签」。所有方法都要在写操作
 * （{@code WriteCommandAction}）里调，自己不开。
 * </p>
 *
 * <h3>写入时的一条不变量</h3>
 * <p>
 * <b>带 {@code extension} 的类型规则一律写成不带 {@code path} 的子节点</b>，不往目录节点自己身上挂。
 * 否则 {@code /src} 的目录备注和「/src 下的 *.java」这两条互不相干的规则会挤在同一个标签上，
 * 改一条就动到另一条。读的时候两种写法都认（目录节点上直接写 extension 也能解析），只是不产出。
 * </p>
 */
class NodeWriter {

    private NodeWriter() {
    }

    /**
     * 找到（必要时建出）某个完整路径对应的 {@code <node>}
     *
     * <p>
     * 沿途会遇到压平过的单链：一个 {@code <node path="main/java">} 顶了两层。要在 {@code main}
     * 这一层落脚时就得把它<b>拆开</b>，见 {@link #split}。
     * </p>
     *
     * @param rootTag  {@code <trees>}
     * @param fullPath 完整路径，空串表示项目根目录
     * @return 路径为空时返回 {@code rootTag} 本身
     */
    static XmlTag ensurePath(XmlTag rootTag, String fullPath) {
        final List<String> segments = NodePaths.segments(fullPath);
        XmlTag cursor = rootTag;
        int index = 0;
        while (index < segments.size()) {
            final int remaining = segments.size() - index;
            XmlTag matched = null;
            int consumed = 0;
            for (XmlTag child : cursor.findSubTags(XmlStorage.NODE)) {
                final List<String> fragment = NodePaths.segments(child.getAttributeValue(XmlStorage.PATH));
                if (fragment.isEmpty() || !fragment.get(0).equals(segments.get(index))) {
                    continue;
                }
                final int common = commonPrefixLength(fragment, segments, index);
                if (common < fragment.size() && common < remaining) {
                    //分叉：已有 a/b/c，要找 a/b/d。先拆到公共部分，剩下的下一轮再走
                    matched = split(child, common);
                    consumed = common;
                } else if (fragment.size() > remaining) {
                    //已有 main/java，只要走到 main：拆开，取外层那个
                    matched = split(child, remaining);
                    consumed = remaining;
                } else {
                    matched = child;
                    consumed = fragment.size();
                }
                break;
            }
            if (null == matched) {
                matched = createNode(cursor, joinSegments(segments, index, segments.size()));
                consumed = remaining;
            }
            cursor = matched;
            index += consumed;
        }
        return cursor;
    }

    /**
     * 把一个顶了好几层的节点拆成两层
     *
     * <p>
     * {@code <node path="main/java" note="x"/>} 拆出 {@code main} 之后变成
     * {@code <node path="main"><node path="java" note="x"/></node>}：<b>属性和子节点全跟着里层走</b>，
     * 它们本来就属于那个更深的路径，留在外层等于把规则整体上移一级。
     * </p>
     *
     * @param tag       要拆的节点
     * @param keepCount 外层保留前几段
     * @return 外层那个节点
     */
    private static XmlTag split(XmlTag tag, int keepCount) {
        final List<String> segments = NodePaths.segments(tag.getAttributeValue(XmlStorage.PATH));
        final String head = joinSegments(segments, 0, keepCount);
        final String rest = joinSegments(segments, keepCount, segments.size());
        final XmlTag parent = tag.getParentTag();
        final XmlTag template = XmlElementFactory.getInstance(tag.getProject())
                .createTagFromText("<" + XmlStorage.NODE + " " + XmlStorage.PATH + "=\"" + head + "\">\n</"
                        + XmlStorage.NODE + ">");
        //先把外层空节点插到原标签前面（此刻它已挂在真实的树上），再把原标签移进去，
        //全程不在游离标签上做增删——那种做法在 PSI 上不稳
        final XmlTag outer = (XmlTag) parent.addBefore(template, tag);
        final XmlTag inner = outer.addSubTag(tag, true);
        inner.setAttribute(XmlStorage.PATH, rest);
        tag.delete();
        return outer;
    }

    /**
     * 在 {@code parent} 末尾建一个只有 path 的空节点
     */
    private static XmlTag createNode(XmlTag parent, String fragment) {
        final XmlTag child = parent.createChildTag(XmlStorage.NODE, parent.getNamespace(), null, false);
        child.setAttribute(XmlStorage.PATH, fragment);
        return parent.addSubTag(child, false);
    }

    /**
     * 这个标签上是不是配了实打实的东西
     *
     * <p>
     * 只有 {@code path} 的是纯容器，删光规则之后应该跟着消失，不然文件里会攒一堆空壳。
     * </p>
     */
    static boolean hasSettings(XmlTag tag) {
        for (String name : XmlStorage.SETTING_ATTRIBUTES) {
            final String value = tag.getAttributeValue(name);
            if (null != value && !value.trim().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 把一个标签上的规则属性全摘掉，只留 {@code path}
     */
    static void stripSettings(XmlTag tag) {
        for (String name : XmlStorage.SETTING_ATTRIBUTES) {
            if (null != tag.getAttribute(name)) {
                tag.setAttribute(name, null);
            }
        }
    }

    /**
     * 删掉一条规则所在的标签，并顺着往上清空壳
     *
     * <p>
     * 标签底下还挂着别的规则时<b>不能整个删掉</b>，只摘掉它自己的属性、留着当容器。
     * 删干净之后父节点可能也空了，一路往上清到还有内容的那层为止。
     * </p>
     */
    static void deleteRule(XmlTag tag) {
        if (tag.getSubTags().length > 0) {
            stripSettings(tag);
            return;
        }
        final XmlTag parent = tag.getParentTag();
        tag.delete();
        prune(parent);
    }

    /**
     * 自下而上删掉没内容的容器节点，碰到 {@code <trees>} 或还有内容的节点就停
     */
    private static void prune(XmlTag tag) {
        XmlTag cursor = tag;
        while (null != cursor && cursor.isValid() && XmlStorage.NODE.equals(cursor.getName())
                && 0 == cursor.getSubTags().length && !hasSettings(cursor)) {
            final XmlTag parent = cursor.getParentTag();
            cursor.delete();
            cursor = parent;
        }
    }

    /**
     * 写入属性：值非空时写入，为空时把已存在的属性删掉
     *
     * <p>
     * 解析时会把缺失的属性归一成 {@code ""}，直接回写就会在文件里堆出 {@code extension="" icon=""}
     * 这类噪音，所以写入前统一在这里过滤一次。{@code setAttribute(name, null)} 是删除语义。
     * </p>
     */
    static void setIfNotEmpty(XmlTag tag, String name, String value) {
        if (null != value && !value.trim().isEmpty()) {
            tag.setAttribute(name, value);
        } else if (null != tag.getAttribute(name)) {
            //仅在属性确实存在时调用，新建标签时不做无用操作
            tag.setAttribute(name, null);
        }
    }

    private static int commonPrefixLength(List<String> fragment, List<String> segments, int offset) {
        int count = 0;
        while (count < fragment.size() && offset + count < segments.size()
                && fragment.get(count).equals(segments.get(offset + count))) {
            count++;
        }
        return count;
    }

    private static String joinSegments(List<String> segments, int from, int to) {
        return String.join("/", segments.subList(from, to));
    }
}
