package com.plugins.infotip.gui.compone;

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
}
