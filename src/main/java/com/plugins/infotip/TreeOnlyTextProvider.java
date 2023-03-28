package com.plugins.infotip;

import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

import static com.plugins.infotip.FileDirectory.setLocationString;

/**
 * 目录树显示备注
 *
 * @author LK
 * @date 2018-04-07 1:18
 */
public class TreeOnlyTextProvider implements TreeStructureProvider, DumbAware {

    @NotNull
    @Override
    public Collection<AbstractTreeNode<?>> modify(@NotNull AbstractTreeNode<?> abstractTreeNode, @NotNull Collection<AbstractTreeNode<?>> collection, ViewSettings viewSettings) {
        psiDirectoryNode(abstractTreeNode);
        collection.forEach(this::psiDirectoryNode);
        return collection;
    }

    @Nullable
    @Override
    public Object getData(@NotNull Collection<AbstractTreeNode<?>> selected, @NotNull String dataId) {
        for (AbstractTreeNode<?> abstractTreeNode : selected) {
            psiDirectoryNode(abstractTreeNode);
        }
        return TreeStructureProvider.super.getData(selected, dataId);
    }

    /**
     * 获取遍历目录
     *
     * @param abstractTreeNode 对象
     */
    private void psiDirectoryNode(AbstractTreeNode<?> abstractTreeNode) {
        setLocationString(abstractTreeNode);
    }

}