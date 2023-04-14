package com.plugins.infotip.storage;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A <code>XmlStorage</code> Class
 *
 * @author lk
 * @version 1.0
 * <p><b>date: 2023/4/13 13:17</b></p>
 */
public class XmlStorage {

    //region 节点常量
    private final static String TREES = "trees";

    private final static String TREE = "tree";

    private final static String PATH = "path";

    private final static String TITLE = "title";

    private final static String EXTENSION = "extension";

    private final static String PRESENTABLE_TEXT = "presentableText";

    private final static String TOOLTIP_TITLE = "tooltipTitle";

    private final static String ICON = "icon";

    private final static String TEXT_COLOR = "textColor";

    private final static String BACKGROUND_COLOR = "backgroundColor";
    //endregion 节点常量

    private final static ConcurrentHashMap<Project, CopyOnWriteArrayList<XmlEntity>> XML_STORAGE_LIST = new ConcurrentHashMap<Project, CopyOnWriteArrayList<XmlEntity>>();


    /**
     * 解析XML
     *
     * @param project 项目
     * @param xmlFile xml文件
     */
    public static synchronized void parsing(Project project, XmlFile xmlFile) {
        if (null == xmlFile) {
            return;
        }
        final CopyOnWriteArrayList<XmlEntity> xmlEntities = XML_STORAGE_LIST.get(project);
        final CopyOnWriteArrayList<XmlEntity> xmlEntities_clone = xmlEntities == null ? new CopyOnWriteArrayList<>() : xmlEntities;
        if (null == xmlEntities) {
            XML_STORAGE_LIST.put(project, xmlEntities_clone);
        }
        final String presentableUrl = project.getPresentableUrl();
        if (presentableUrl == null) {
            return;
        }
        xmlFile.accept(new XmlRecursiveElementVisitor() {
            @Override
            public void visitElement(final @NotNull PsiElement element) {
                super.visitElement(element);
                if (element instanceof XmlTag) {
                    //针对节点执行不同的解析方案
                    XmlTag tag = (XmlTag) element;
                    if (TREE.equals(tag.getName())) {
                        XmlEntity tree = tree(tag);
                        if (null != tree) {
                            xmlEntities_clone.add(tree);
                        }
                    }
                }
            }
        });
    }


    public static void clear(Project project) {
        final List<XmlEntity> xmlEntities = XML_STORAGE_LIST.get(project);
        if (null != xmlEntities) {
            xmlEntities.clear();
        }
    }

    public static List<XmlEntity> getXmlEntity(Project project) {
        return XML_STORAGE_LIST.get(project);
    }

    /**
     * 修改路径
     *
     * @param project   项目
     * @param xmlEntity 目录对象
     */
    public static synchronized void modify(Project project, XmlFile fileDirectoryXml, XmlEntity xmlEntity) {
        final XmlFile xmlFile = XmlFileUtils.getXmlFile(project);
        if (null != xmlFile && null != xmlEntity) {
            final XmlTag childTag = xmlEntity.getTag();
            if (null == childTag) {
                create(project, fileDirectoryXml, xmlEntity);
            } else {
                WriteCommandAction.runWriteCommandAction(project, () -> {
                    childTag.setAttribute(PATH, xmlEntity.getPath());
                    childTag.setAttribute(TITLE, xmlEntity.getTitle());
                    childTag.setAttribute(EXTENSION, xmlEntity.getExtension());
                    childTag.setAttribute(PRESENTABLE_TEXT, xmlEntity.getPresentableText());
                    childTag.setAttribute(TOOLTIP_TITLE, xmlEntity.getTooltipTitle());
                    childTag.setAttribute(ICON, xmlEntity.getIcon());
                    childTag.setAttribute(TEXT_COLOR, xmlEntity.getTextColor());
                    childTag.setAttribute(BACKGROUND_COLOR, xmlEntity.getBackgroundColor());
                    XmlFileUtils.saveFileXml(project);
                });
            }
        }
    }


    public static synchronized void remove(XmlFile xmlFile, Project project, XmlEntity xmlEntity) {
        XmlDocument document = xmlFile.getDocument();
        if (null == document || null == xmlEntity) {
            return;
        }
        xmlFile.accept(new XmlRecursiveElementVisitor() {
            @Override
            public void visitElement(final @NotNull PsiElement element) {
                super.visitElement(element);
                if (element instanceof XmlTag) {
                    //针对节点执行不同的解析方案
                    XmlTag tag = (XmlTag) element;
                    if (TREE.equals(tag.getName())) {
                        XmlEntity tree = tree(tag);
                        if (null != tree) {
                            if (xmlEntity.getPath().equals(tree.getPath())) {
                                WriteCommandAction.runWriteCommandAction(project, () -> {
                                    tag.delete();
                                    XmlFileUtils.saveFileXml(project);
                                });
                            }
                        }
                    }
                }
            }
        });
    }


    /**
     * 创建新标签
     *
     * @param xmlFile   xml
     * @param project   项目
     * @param xmlEntity 类型
     */
    public static synchronized void create(Project project, XmlFile xmlFile, XmlEntity xmlEntity) {
        XmlDocument document = xmlFile.getDocument();
        if (null == document) {
            return;
        }
        XmlTag rootTag = document.getRootTag();
        if (null != rootTag) {
            if (TREES.equals(rootTag.getName())) {
                XmlTag childTag = rootTag.createChildTag(TREE, rootTag.getNamespace(), null, false);
                childTag.setAttribute(PATH, xmlEntity.getPath());
                childTag.setAttribute(TITLE, xmlEntity.getTitle());
                childTag.setAttribute(EXTENSION, xmlEntity.getExtension());
                childTag.setAttribute(PRESENTABLE_TEXT, xmlEntity.getPresentableText());
                childTag.setAttribute(TOOLTIP_TITLE, xmlEntity.getTooltipTitle());
                childTag.setAttribute(ICON, xmlEntity.getIcon());
                childTag.setAttribute(TEXT_COLOR, xmlEntity.getTextColor());
                childTag.setAttribute(BACKGROUND_COLOR, xmlEntity.getBackgroundColor());
                WriteCommandAction.runWriteCommandAction(project, new Runnable() {
                    @Override
                    public void run() {
                        rootTag.addSubTag(childTag, false);
                        XmlFileUtils.saveFileXml(project);
                    }
                });
            }
        }
    }

    private static XmlEntity tree(XmlTag tag) {
        XmlEntity xmlEntity = new XmlEntity();
        XmlAttribute xml_path = tag.getAttribute(PATH);
        XmlAttribute xml_title = tag.getAttribute(TITLE);
        XmlAttribute xml_extension = tag.getAttribute(EXTENSION);
        XmlAttribute xml_presentable_text = tag.getAttribute(PRESENTABLE_TEXT);
        XmlAttribute xml_tooltip_title = tag.getAttribute(TOOLTIP_TITLE);
        XmlAttribute xml_icons = tag.getAttribute(ICON);
        XmlAttribute xml_text_color = tag.getAttribute(TEXT_COLOR);
        XmlAttribute xml_background_color = tag.getAttribute(BACKGROUND_COLOR);
        if (xml_path != null) {
            xmlEntity.setPath(xml_path.getValue()).setTitle(xml_title == null ? "" : xml_title.getValue()).setExtension(xml_extension == null ? "" : xml_extension.getValue()).setPresentableText(xml_presentable_text == null ? "" : xml_presentable_text.getValue()).setTooltipTitle(xml_tooltip_title == null ? "" : xml_tooltip_title.getValue()).setIcon(xml_icons == null ? "" : xml_icons.getValue()).setTextColor(xml_text_color == null ? "" : xml_text_color.getValue()).setBackgroundColor(xml_background_color == null ? "" : xml_background_color.getValue()).setTag(tag);
            return xmlEntity;
        }
        return null;
    }

}
