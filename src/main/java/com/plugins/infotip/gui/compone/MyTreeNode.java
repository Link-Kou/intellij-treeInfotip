package com.plugins.infotip.gui.compone;

import javax.swing.Icon;
import javax.swing.tree.DefaultMutableTreeNode;

/**
 * A <code>MyTreeNode</code> Class
 *
 * @author lk
 * @version 1.0
 * <p><b>date: 2023/4/13 22:02</b></p>
 */
public class MyTreeNode extends DefaultMutableTreeNode {
    private Object UserEntity;

    /**
     * 列表左侧显示的图标
     * <p>
     * 在构建节点时算好而不是渲染时现算：渲染器每帧对每个可见行都要调一次，
     * 而算图标要碰 VFS 和 FileTypeManager。
     * </p>
     */
    private Icon icon;

    /**
     * 规则指向的路径在磁盘上已经不存在，渲染时整行标红
     */
    private boolean missing;

    /**
     * 前面已经有一条同路径同扩展名的规则了，这条被它盖住、永远不会生效，渲染时整行灰掉
     * <p>
     * 和 {@link #missing} 一样是建节点时算好的：判据只看配置本身（见
     * {@code TreesUtils.ruleKey}），不碰 VFS。
     * </p>
     */
    private boolean shadowed;

    public MyTreeNode(Object userObject) {
        super(userObject);
    }

    public Object getUserEntity() {
        return UserEntity;
    }

    public MyTreeNode setUserEntity(Object userEntity) {
        UserEntity = userEntity;
        return this;
    }

    public Icon getIcon() {
        return icon;
    }

    public MyTreeNode setIcon(Icon icon) {
        this.icon = icon;
        return this;
    }

    public boolean isMissing() {
        return missing;
    }

    public MyTreeNode setMissing(boolean missing) {
        this.missing = missing;
        return this;
    }

    public boolean isShadowed() {
        return shadowed;
    }

    public MyTreeNode setShadowed(boolean shadowed) {
        this.shadowed = shadowed;
        return this;
    }
}
