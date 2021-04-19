package com.plugins.infotip;

import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.vfs.VirtualFile;
import com.plugins.infotip.parsing.model.ListTreeInfo;
import com.plugins.infotip.parsing.model.ProjectInfo;
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
        PsiDirectoryNode(abstractTreeNode);
        return collection;
    }

    @Nullable
    @Override
    public Object getData(@NotNull Collection<AbstractTreeNode<?>> selected, @NotNull String dataId) {
        for (AbstractTreeNode abstractTreeNode : selected) {
            PsiDirectoryNode(abstractTreeNode);
        }
        return null;
    }

    /**
     * 获取遍历目录
     *
     * @param abstractTreeNode
     */
    private void PsiDirectoryNode(AbstractTreeNode abstractTreeNode) {
        //文件夹类型
        if (abstractTreeNode instanceof PsiDirectoryNode) {
            ProjectInfo.getParsingConfigureXML(abstractTreeNode.getProject());
            List<ListTreeInfo> listTreeInfos = ProjectInfo.listTreeInfos;
            if (listTreeInfos != null) {
                if (((PsiDirectoryNode) abstractTreeNode).getValue() != null) {
                    VirtualFile pdn = ((PsiDirectoryNode) abstractTreeNode).getValue().getVirtualFile();
                    for (ListTreeInfo listTreeInfo : listTreeInfos) {
                        if (listTreeInfo != null) {
                            if (pdn.getPresentableUrl().equals(listTreeInfo.getPath())) {
                                //设置备注
                                abstractTreeNode.getPresentation().setLocationString(listTreeInfo.getTitle());
                            }
                        }
                    }
                }
            }
        } else {
            if (abstractTreeNode != null && abstractTreeNode.getValue() != null) {
                ProjectInfo.getParsingConfigureXML(abstractTreeNode.getProject());
                List<ListTreeInfo> listTreeInfos = ProjectInfo.listTreeInfos;
                Method[] methods = abstractTreeNode.getClass().getMethods();
                for (Method method : methods) {
                    if ("getVirtualFile".equals(method.getName())) {
                        method.setAccessible(true);
                        try {
                            Object invoke = method.invoke(abstractTreeNode);
                            if (invoke instanceof VirtualFile) {
                                VirtualFile pdn = (VirtualFile) invoke;
                                for (ListTreeInfo listTreeInfo : listTreeInfos) {
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


}