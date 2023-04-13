package com.plugins.infotip.trees;

import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.impl.nodes.AbstractPsiBasedNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.plugins.infotip.storage.XmlEntity;
import com.plugins.infotip.storage.XmlStorage;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * A <code>TreesUtils</code> Class
 *
 * @author lk
 * @version 1.0
 * <p><b>date: 2023/4/13 14:47</b></p>
 */
public class TreesUtils {

    /**
     * 匹配路径
     *
     * @param virtualFile 文件对象
     * @return boolean
     */
    public static XmlEntity getMatchPath(VirtualFile virtualFile, Project project) {
        List<XmlEntity> xml = XmlStorage.getXmlEntity(project);
        for (XmlEntity listTreeInfo : xml) {
            if (listTreeInfo != null) {
                try {
                    String basePath = project.getPresentableUrl();
                    String canonicalPath = virtualFile.getCanonicalPath();
                    if (null != basePath && null != canonicalPath) {
                        int beginIndex = basePath.length();
                        int endIndex = canonicalPath.length();
                        if (beginIndex <= endIndex) {
                            String subBasePath = canonicalPath.substring(beginIndex, endIndex);
                            if (subBasePath.equals(listTreeInfo.getPath())) {
                                return listTreeInfo;
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    /**
     * 获取 VirtualFile
     *
     * @param abstractTreeNode 对象
     */
    public static VirtualFile getVirtualFile(AbstractTreeNode<?> abstractTreeNode) {
        if (null != abstractTreeNode) {
            Method[] methods1 = abstractTreeNode.getClass().getMethods();
            Object value = abstractTreeNode.getValue();
            if (null != value) {
                Method[] methods2 = value.getClass().getMethods();
                VirtualFile virtualFile2 = getVirtualFile(methods2, value);
                if (null == virtualFile2) {
                    if (abstractTreeNode instanceof AbstractPsiBasedNode) {
                        final AbstractPsiBasedNode abstractTreeNode1 = (AbstractPsiBasedNode) abstractTreeNode;
                        Method[] methods3 = AbstractPsiBasedNode.class.getDeclaredMethods();
                        return getVirtualFileForValue(methods3, abstractTreeNode1);
                    }
                }
            }
            return getVirtualFile(methods1, abstractTreeNode);
        }
        return null;
    }

    /**
     * 获取到 VirtualFile
     *
     * @param methods 方法
     * @param o       对象
     * @return VirtualFile
     */
    private static VirtualFile getVirtualFile(Method[] methods, Object o) {
        for (Method method : methods) {
            if ("getVirtualFile".equals(method.getName())) {
                method.setAccessible(true);
                try {
                    Object invoke = method.invoke(o);
                    if (invoke instanceof VirtualFile) {
                        return (VirtualFile) invoke;
                    }
                } catch (IllegalAccessException | InvocationTargetException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    private static VirtualFile getVirtualFileForValue(Method[] methods, Object o) {
        for (Method method : methods) {
            if ("getVirtualFileForValue".equals(method.getName())) {
                method.setAccessible(true);
                try {
                    Object invoke = method.invoke(o);
                    if (invoke instanceof VirtualFile) {
                        return (VirtualFile) invoke;
                    }
                } catch (IllegalAccessException | InvocationTargetException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }
}
