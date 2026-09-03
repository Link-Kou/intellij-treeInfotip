package com.plugins.infotip;

import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.DumbAware;
import com.plugins.infotip.trees.TreesStyle;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;


/**
 * 目录树显示备注
 *
 * @author LK
 */
public class TreeOnlyTextProvider implements TreeStructureProvider, DumbAware {

    @NotNull
    @Override
    public Collection<AbstractTreeNode<?>> modify(@NotNull AbstractTreeNode<?> abstractTreeNode, @NotNull Collection<AbstractTreeNode<?>> collection, ViewSettings viewSettings) {
        collection.forEach(this::psiDirectoryNode);
        return collection;
    }

    //原来这里还覆盖了 getData(Collection, String)，在里面对选中的节点再 setStyle 一遍。
    //那个方法被标了 @ApiStatus.ScheduledForRemoval，Marketplace 的 Plugin Verifier 会报出来，
    //而且它做的事本来就是多余的：样式在 modify 里已经对全部节点应用过，
    //另一个入口 IgnoreViewNodeDecorator 也会再兜一遍，删掉不影响渲染。

    /**
     * 获取遍历目录
     *
     * @param abstractTreeNode 对象
     */
    private void psiDirectoryNode(AbstractTreeNode<?> abstractTreeNode) {
        TreesStyle.setStyle(abstractTreeNode);
    }

}