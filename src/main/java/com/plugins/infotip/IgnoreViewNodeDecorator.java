/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018 hsz Jakub Chrzanowski <jakub@hsz.mobi>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.plugins.infotip;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ProjectViewNodeDecorator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.ui.PackageDependenciesNode;
import com.intellij.ui.ColoredTreeCellRenderer;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * 项目目录视图
 *
 * @author LK
 * @date 2018-04-07 1:18
 */
public class IgnoreViewNodeDecorator implements ProjectViewNodeDecorator {

    public IgnoreViewNodeDecorator(@NotNull Project project) {

    }

    @Override
    public void decorate(ProjectViewNode node, PresentationData data) {
        if (node != null && node.getValue() != null) {
            List<XmlEntity> xml = XmlParsing.getXml();
            Method[] methods = node.getClass().getMethods();
            for (Method method : methods) {
                if ("getVirtualFile".equals(method.getName())) {
                    method.setAccessible(true);
                    try {
                        Object invoke = method.invoke(node);
                        if (invoke instanceof VirtualFile) {
                            VirtualFile pdn = (VirtualFile) invoke;
                            for (XmlEntity listTreeInfo : xml) {
                                if (listTreeInfo != null) {
                                    if (pdn.getPresentableUrl().equals(listTreeInfo.getPath())) {
                                        //设置备注
                                        data.setLocationString(listTreeInfo.getTitle());
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

    @Override
    public void decorate(PackageDependenciesNode node, ColoredTreeCellRenderer cellRenderer) {
    }
}
