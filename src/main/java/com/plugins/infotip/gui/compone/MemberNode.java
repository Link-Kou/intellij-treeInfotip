package com.plugins.infotip.gui.compone;

import com.intellij.psi.PsiElement;

import javax.swing.Icon;
import javax.swing.tree.DefaultMutableTreeNode;

/**
 * A <code>MemberNode</code> Class
 * <p>
 * 「文件成员」列表的节点：名字、后面跟的注释、图标，以及跳转要用的 PSI 元素。
 * </p>
 * <p>
 * 名字和注释分开存而不是拼成一个字符串，是为了渲染时给注释单独上灰色。图标直接取
 * Structure View 给的那个，和 IDE 自带的结构视图看起来一致。
 * </p>
 *
 * @author yc556&claude-opus-5
 * @version 1.0
 */
public class MemberNode extends DefaultMutableTreeNode {

    /**
     * 成员名，来自 Structure View 的 {@code ItemPresentation#getPresentableText}
     */
    private final String name;

    /**
     * 读出来的注释，没有就是空串
     */
    private String comment = "";

    private Icon icon;

    /**
     * 单击跳转用。文件一改就可能失效，用之前必须查 {@code isValid()}
     */
    private PsiElement element;

    public MemberNode(String name) {
        super(name);
        this.name = null == name ? "" : name;
    }

    public String getName() {
        return name;
    }

    public String getComment() {
        return comment;
    }

    public MemberNode setComment(String comment) {
        this.comment = null == comment ? "" : comment;
        return this;
    }

    public Icon getIcon() {
        return icon;
    }

    public MemberNode setIcon(Icon icon) {
        this.icon = icon;
        return this;
    }

    public PsiElement getElement() {
        return element;
    }

    public MemberNode setElement(PsiElement element) {
        this.element = element;
        return this;
    }
}
