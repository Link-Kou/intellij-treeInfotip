package com.plugins.infotip;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A <code>XmlParsing</code> Class
 *
 * @author lk
 * @version 1.0
 * @date 2021/6/7 17:12
 */
public class XmlParsing {

    private final static String TAGTREES = "trees";

    private final static String TAGTREE = "tree";

    private final static String PATH = "path";

    private final static String TITLE = "title";

    private final static String EXTENSION = "extension";

    private final static List<XmlEntity> XMLLIST = new CopyOnWriteArrayList<>();

    /**
     * 解析XML
     *
     * @param project 项目
     * @param xmlFile xml文件
     */
    public static void parsing(Project project, XmlFile xmlFile) {
        if (null == xmlFile) {
            return;
        }
        XMLLIST.clear();
        final String presentableUrl = project.getPresentableUrl();
        if (presentableUrl == null) {
            return;
        }
        xmlFile.accept(new XmlRecursiveElementVisitor() {
            @Override
            public void visitElement(final PsiElement element) {
                super.visitElement(element);
                if (element instanceof XmlTag) {
                    //针对节点执行不同的解析方案
                    XmlTag tag = (XmlTag) element;
                    if (TAGTREE.equals(tag.getName())) {
                        XmlEntity tree = tree(tag, presentableUrl);
                        if (null != tree) {
                            XMLLIST.add(tree);
                        }
                    }
                }
            }
        });
    }

    /**
     * 解析XML
     *
     * @param project 项目
     * @param xmlFile xml文件
     */
    public static List<XmlEntity> getRefreshXml(Project project, XmlFile xmlFile) {
        parsing(project, xmlFile);
        return XMLLIST;
    }

    /**
     * 解析XML
     */
    public static List<XmlEntity> getXml() {
        return XMLLIST;
    }


    public static void clear() {
        XMLLIST.clear();
    }

    /**
     * 创建新标签
     *
     * @param xmlFile   xml
     * @param project   项目
     * @param path      路径
     * @param title     标题
     * @param extension 类型
     */
    public static void createPath(XmlFile xmlFile, Project project, String path, String title, String extension) {
        XmlDocument document = xmlFile.getDocument();
        if (null == document) {
            return;
        }
        XmlTag rootTag = document.getRootTag();
        if (null != rootTag) {
            if (TAGTREES.equals(rootTag.getName())) {
                XmlTag childTag = rootTag.createChildTag(TAGTREE, rootTag.getNamespace(), null, false);
                childTag.setAttribute(PATH, path);
                childTag.setAttribute(TITLE, title);
                childTag.setAttribute(EXTENSION, extension);
                WriteCommandAction.runWriteCommandAction(project, new Runnable() {
                    @Override
                    public void run() {
                        rootTag.addSubTag(childTag, false);
                        FileDirectory.saveFileDirectoryXml(project, document.getText());
                    }
                });
            }
        }
    }

    /**
     * 修改路径
     *
     * @param childTag 标签
     * @param title    标题
     * @param project  项目
     */
    public static void modifyPath(XmlTag childTag, String title, Project project) {
        if (null != childTag) {
            WriteCommandAction.runWriteCommandAction(project, new Runnable() {
                @Override
                public void run() {
                    childTag.setAttribute(TITLE, title);
                }
            });
        }
    }


    private static XmlEntity tree(XmlTag tag, String presentableUrl) {
        XmlEntity xmlEntity = new XmlEntity();
        XmlAttribute xmlpath = tag.getAttribute(PATH);
        XmlAttribute xmltitle = tag.getAttribute(TITLE);
        XmlAttribute xmlextension = tag.getAttribute(EXTENSION);
        if (xmlpath != null) {
            String xmlextensionvalue = null != xmlextension ? xmlextension.getValue() : "";
            String xmltitlevalue = null != xmltitle ? xmltitle.getValue() : "";
            String treepath = presentableUrl + xmlpath.getValue();
            xmlEntity.setTitle(xmltitlevalue)
                    .setExtension(xmlextensionvalue)
                    .setTag(tag)
                    .setPath(treepath);
            return xmlEntity;
        }
        return null;
    }

}
