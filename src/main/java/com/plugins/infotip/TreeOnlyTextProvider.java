package com.plugins.infotip;

import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

/**
 * 目录树显示备注
 *
 * @author LK
 * @date 2018-04-07 1:18
 */
public class TreeOnlyTextProvider implements TreeStructureProvider {

    @NotNull
    @Override
    public Collection<AbstractTreeNode<?>> modify(@NotNull AbstractTreeNode<?> abstractTreeNode, @NotNull Collection<AbstractTreeNode<?>> collection, ViewSettings viewSettings) {
        psiDirectoryNode(abstractTreeNode);
        return collection;
    }

    @Nullable
    @Override
    public Object getData(@NotNull Collection<AbstractTreeNode<?>> selected, @NotNull String dataId) {
        for (AbstractTreeNode<?> abstractTreeNode : selected) {
            psiDirectoryNode(abstractTreeNode);
        }
        return null;
    }

    /**
     * 获取遍历目录
     *
     * @param abstractTreeNode 对象
     */
    private void psiDirectoryNode(AbstractTreeNode<?> abstractTreeNode) {
        if (abstractTreeNode != null && abstractTreeNode.getValue() != null) {
            List<XmlEntity> xml = XmlParsing.getXml();
            Method[] methods = abstractTreeNode.getClass().getMethods();
            for (Method method : methods) {
                if ("getVirtualFile".equals(method.getName())) {
                    method.setAccessible(true);
                    try {
                        Object invoke = method.invoke(abstractTreeNode);
                        if (invoke instanceof VirtualFile) {
                            VirtualFile pdn = (VirtualFile) invoke;
                            for (XmlEntity listTreeInfo : xml) {
                                if (listTreeInfo != null) {
                                    if (pdn.getPresentableUrl().equals(listTreeInfo.getPath())) {
                                        //设置备注
                                        abstractTreeNode.getPresentation().setLocationString(listTreeInfo.getTitle());
                                    }
                                }
                            }
                        }
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }


}