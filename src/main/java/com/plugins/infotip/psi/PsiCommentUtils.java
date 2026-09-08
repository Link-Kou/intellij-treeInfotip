package com.plugins.infotip.psi;

import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 从 PSI 元素上读出「这一项的注释」
 * <p>
 * 刻意不依赖任何语言插件的注释类型（{@code PsiDocComment}、{@code JSDocComment} 等都在各自的
 * 插件里，本插件不声明依赖，直接引用会在没装那些插件的 IDE 上 {@code NoClassDefFoundError}）。
 * 这里只认平台自带的 {@link PsiComment}，靠注释文本开头的记号分类：{@code /**} 是文档注释、
 * {@code /*} 是多行注释、{@code //} 和 {@code #} 是单行注释。副作用是 Java / TS / JS / Kotlin /
 * Python / Go 这些都能一起支持，代价是判断不了语言层面的语义。
 * </p>
 * <p>
 * 优先级严格按四级来，命中一级就不再往下找：文档注释 → 多行注释 → 同行尾部的单行注释 →
 * 顶部连续的单行注释（空行即断开）。
 * </p>
 *
 * @author yc556&claude-opus-5
 * @version 1.0
 */
public class PsiCommentUtils {

    /**
     * 显示用的长度上限，超了截断加省略号。列表宽度有限，整段 JavaDoc 铺开没法看
     */
    private static final int MAX_LENGTH = 120;

    /**
     * {@link #carrier} 往上抬的层数上限。TS 的 {@code const X = () => {}} 只要抬两层就到语句
     */
    private static final int MAX_LIFT = 4;

    /**
     * {@link #startsParent} 往前走的叶子数上限。压缩过的 js 整个文件就一行，不设上限会一路
     * 走到文件开头
     */
    private static final int MAX_WALK = 64;

    /**
     * 读注释，找不到返回空串
     *
     * @param element 方法 / 属性 / 类的 PSI 元素
     * @return 清理过的单行文本
     */
    public static String read(PsiElement element) {
        if (null == element || !element.isValid()) {
            return "";
        }
        //注释不一定挂在结构视图给的那个元素上，先抬到真正承载它的那一层
        final List<String> leading = leadingComments(carrier(element));
        //一、文档注释；从最近的一条往前找，正常只会有一条
        for (int i = leading.size() - 1; i >= 0; i--) {
            final String raw = leading.get(i);
            if (isDoc(raw)) {
                final String text = clean(raw);
                //空的 /** */ 不算命中，继续往下一级找
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }
        //二、多行注释
        for (int i = leading.size() - 1; i >= 0; i--) {
            final String raw = leading.get(i);
            if (isBlock(raw)) {
                final String text = clean(raw);
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }
        //三、同行尾部的单行注释
        final String trailing = trailingComment(element);
        if (!trailing.isEmpty()) {
            return trailing;
        }
        //四、顶部连续的单行注释，按原顺序拼起来
        final StringBuilder builder = new StringBuilder();
        for (String raw : leading) {
            if (isLine(raw)) {
                append(builder, clean(raw));
            }
        }
        return truncate(builder.toString());
    }

    /**
     * 找到真正承载前置注释的那一层
     * <p>
     * 结构视图给的元素不一定是注释挂靠的地方，TS / TSX 里最典型的就是箭头函数组件：
     * </p>
     * <pre>
     * &#47;** 承运商招募报名审核工作台。 *&#47;
     * const CarrierRecruitRegPage: React.FC = () =&gt; {}
     * </pre>
     * <p>
     * 结构视图给的是<b>箭头函数</b>，而 JSDoc 挂在整条 {@code const} 语句上，中间隔着
     * {@code const CarrierRecruitRegPage: React.FC =} 一串 token，在箭头函数这一层往前找
     * 永远找不到。所以先往上走到语句那一层再找。
     * </p>
     * <p>
     * 往上走的条件是<b>当前元素就在父节点的开头</b>，即中间只隔着同一行的 token 和注释。中间
     * 夹了换行又夹着实质代码，就说明父节点是更大的结构（类、代码块、多行的对象字面量），它的
     * 注释不属于当前这个成员。Java 里类的第一个方法就是靠这条不把类的 JavaDoc 认成方法的注释。
     * 代价是挤在一行里的对象字面量（{@code {a: 1, b: 2}}）的属性会抬到字面量本身，宁可多显示
     * 一句也不要少显示。
     * </p>
     */
    private static PsiElement carrier(PsiElement element) {
        PsiElement current = element;
        for (int lifted = 0; lifted < MAX_LIFT; lifted++) {
            final PsiElement parent = current.getParent();
            if (null == parent || parent instanceof PsiFile || !startsParent(parent, current)) {
                break;
            }
            current = parent;
        }
        return current;
    }

    /**
     * {@code current} 是不是就在 {@code parent} 的开头——中间只隔着同一行的 token 和注释
     * <p>
     * 逐个叶子往前走而不是直接切 {@code getText()}：父节点是个类的时候要把整个类的源码拼成
     * 字符串，太贵。正常情况走的步数就是同一行上那几个 token 加上一条注释，压缩过的 js 整个
     * 文件只有一行，靠 {@link #MAX_WALK} 兜住。
     * </p>
     * <p>
     * <b>注释一律跳过</b>（它正是要找的东西，隔了几行都算），其余实质 token <b>只要和当前元素
     * 之间夹了换行就判否</b>。JSDoc 的两种挂法因此都能过：挂成语句第一个子节点时，夹在中间的
     * {@code const} 和当前元素同行、换行只出现在注释那一侧；挂成前一个兄弟时，走到 {@code const}
     * 之前就已经出了父节点的范围。而 Java 里类的第一个方法过不了——{@code class X} 的花括号和
     * 方法之间必然有换行。
     * </p>
     */
    private static boolean startsParent(PsiElement parent, PsiElement current) {
        final int start = parent.getTextRange().getStartOffset();
        boolean crossedLine = false;
        PsiElement leaf = PsiTreeUtil.prevLeaf(current, true);
        for (int walked = 0; null != leaf && walked < MAX_WALK; walked++, leaf = PsiTreeUtil.prevLeaf(leaf, true)) {
            //已经走出 parent 的范围，说明 current 就在 parent 的开头
            if (leaf.getTextRange().getStartOffset() < start) {
                return true;
            }
            if (null != PsiTreeUtil.getParentOfType(leaf, PsiComment.class, false)) {
                continue;
            }
            if (leaf.getText().indexOf('\n') >= 0) {
                crossedLine = true;
                continue;
            }
            //换行另一侧的实质代码不属于当前元素的声明
            if (crossedLine && !(leaf instanceof PsiWhiteSpace)) {
                return false;
            }
        }
        //一路走到文件开头都没遇到别的代码，或者走得太远不再判断
        return null == leaf;
    }

    /**
     * 收集元素上方紧邻的注释，按文档顺序返回
     * <p>
     * 分两步是因为不同语言把注释挂在不同地方：Java 的 JavaDoc、Kotlin 的 KDoc 是方法元素**自己的
     * 第一个子节点**，而 JS / TS / Go 里的注释多半是方法的**前一个兄弟节点**。先扫自己的头部子
     * 节点，一条都没有再往前找兄弟。
     * </p>
     * <p>
     * 往前找时用 {@link PsiTreeUtil#prevLeaf} 逐个叶子走，再用
     * {@link PsiTreeUtil#getParentOfType} 把叶子抬回它所属的整条注释——JSDoc 在 PSI 里是个复合
     * 元素，直接拿叶子只能拿到 {@code /**} 这几个字符。碰到空行（一段空白里有两个以上换行）就停，
     * 这是需求里「必须连在一起每个空行分隔」那条。
     * </p>
     */
    private static List<String> leadingComments(PsiElement element) {
        final List<String> result = new ArrayList<String>();
        for (PsiElement child = element.getFirstChild(); null != child; child = child.getNextSibling()) {
            if (child instanceof PsiWhiteSpace) {
                continue;
            }
            if (child instanceof PsiComment) {
                final String text = child.getText();
                if (null != text) {
                    result.add(text);
                }
                continue;
            }
            //第一个不是注释也不是空白的子节点，头部就到这儿了
            break;
        }
        if (!result.isEmpty()) {
            return result;
        }
        PsiComment last = null;
        for (PsiElement leaf = PsiTreeUtil.prevLeaf(element, true); null != leaf; leaf = PsiTreeUtil.prevLeaf(leaf, true)) {
            if (leaf instanceof PsiWhiteSpace) {
                final String text = leaf.getText();
                if (null == text || blankLine(text)) {
                    break;
                }
                continue;
            }
            final PsiComment comment = PsiTreeUtil.getParentOfType(leaf, PsiComment.class, false);
            if (null == comment) {
                //遇到非注释、非空白的叶子（实质代码），检查它和最后收集到的注释是否在同一行
                if (null != last && onSameLine(leaf, last)) {
                    //在同一行，说明那条注释是上一行代码的行尾注释，不属于当前元素，移除它
                    if (!result.isEmpty()) {
                        result.remove(result.size() - 1);
                    }
                }
                break;
            }
            //复合注释的每个叶子都会走到这儿，同一条只收一次
            if (comment != last) {
                final String text = comment.getText();
                if (null != text) {
                    result.add(0, text);
                }
                last = comment;
            }
        }
        return result;
    }

    /**
     * 同行尾部的单行注释
     * <p>
     * 从元素最深处的第一个叶子往后走,一碰到带换行的叶子就停——「同行」就是这个意思。
     * 一路上遇到的第一条单行注释就是要的那条。
     * </p>
     */
    private static String trailingComment(PsiElement element) {
        PsiElement leaf = element;
        while (null != leaf.getFirstChild()) {
            leaf = leaf.getFirstChild();
        }
        for (; null != leaf; leaf = PsiTreeUtil.nextLeaf(leaf, true)) {
            final String text = leaf.getText();
            //PSI 元素失效时 getText() 会返回 null（比如文件被删了）
            if (null == text) {
                break;
            }
            if (leaf instanceof PsiComment && isLine(text)) {
                final String cleaned = clean(text);
                if (!cleaned.isEmpty()) {
                    return cleaned;
                }
            }
            if (text.indexOf('\n') >= 0) {
                break;
            }
        }
        return "";
    }

    /**
     * 一段空白里是不是含空行，即有两个及以上换行
     */
    private static boolean blankLine(String text) {
        return text.indexOf('\n') != text.lastIndexOf('\n');
    }

    /**
     * 两个元素是否在同一行（中间没有换行符）
     */
    private static boolean onSameLine(PsiElement a, PsiElement b) {
        if (null == a || null == b) {
            return false;
        }
        final int end = Math.max(a.getTextRange().getEndOffset(), b.getTextRange().getEndOffset());
        //从较早的元素往后走到较晚的元素，中间遇到换行就判否
        for (PsiElement leaf = a.getTextRange().getStartOffset() < b.getTextRange().getStartOffset() ? a : b;
             null != leaf && leaf.getTextRange().getStartOffset() < end;
             leaf = PsiTreeUtil.nextLeaf(leaf, true)) {
            final String text = leaf.getText();
            if (null != text && text.indexOf('\n') >= 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean isDoc(String raw) {
        return raw.startsWith("/**");
    }

    private static boolean isBlock(String raw) {
        return raw.startsWith("/*") && !raw.startsWith("/**");
    }

    private static boolean isLine(String raw) {
        //`#` 是 Python / Shell / YAML 的单行注释，`--` 是 SQL 的
        return raw.startsWith("//") || raw.startsWith("#") || raw.startsWith("--");
    }

    /**
     * 把一条注释的原文清理成一行能显示的文本：去掉注释记号、去掉每行行首的 {@code *}、
     * 压掉多余空白、超长截断
     * <p>
     * {@code @param} / {@code @return} 这类标签行单独攒一份：正文有内容就只要正文，正文全是
     * 标签（比如只写了 {@code @deprecated}）才退回去用它们，总比显示空白好。
     * </p>
     */
    private static String clean(String raw) {
        final StringBuilder all = new StringBuilder();
        final StringBuilder body = new StringBuilder();
        for (String line : raw.split("\n")) {
            String text = line.trim();
            text = stripPrefix(text, "/**");
            text = stripPrefix(text, "/*");
            text = stripPrefix(text, "//");
            text = stripPrefix(text, "#");
            text = stripPrefix(text, "--");
            if (text.endsWith("*/")) {
                text = text.substring(0, text.length() - 2).trim();
            }
            //中间行行首的 * 是排版用的，不是内容
            while (text.startsWith("*")) {
                text = text.substring(1).trim();
            }
            if (text.isEmpty()) {
                continue;
            }
            append(all, text);
            if ('@' != text.charAt(0)) {
                append(body, text);
            }
        }
        return truncate(body.length() > 0 ? body.toString() : all.toString());
    }

    private static String stripPrefix(String text, String prefix) {
        return text.startsWith(prefix) ? text.substring(prefix.length()).trim() : text;
    }

    private static void append(StringBuilder builder, String text) {
        if (text.isEmpty()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(' ');
        }
        //连续空白压成一个，源码里为了对齐加的空格没必要带进列表
        builder.append(text.replaceAll("\\s+", " "));
    }

    private static String truncate(String text) {
        final String trimmed = text.trim();
        return trimmed.length() > MAX_LENGTH ? trimmed.substring(0, MAX_LENGTH) + "…" : trimmed;
    }
}
