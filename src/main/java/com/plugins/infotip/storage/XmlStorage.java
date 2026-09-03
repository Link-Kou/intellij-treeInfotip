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

    private final static String STRIKETHROUGH = "strikethrough";
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
        XML_STORAGE_LIST.remove(project);
        final CopyOnWriteArrayList<XmlEntity> xmlEntities_clone = new CopyOnWriteArrayList<>();
        XML_STORAGE_LIST.put(project, xmlEntities_clone);
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
                    setAttributeIfNotEmpty(childTag, PATH, xmlEntity.getPath());
                    setAttributeIfNotEmpty(childTag, TITLE, xmlEntity.getTitle());
                    setAttributeIfNotEmpty(childTag, EXTENSION, xmlEntity.getExtension());
                    setAttributeIfNotEmpty(childTag, PRESENTABLE_TEXT, xmlEntity.getPresentableText());
                    setAttributeIfNotEmpty(childTag, TOOLTIP_TITLE, xmlEntity.getTooltipTitle());
                    setAttributeIfNotEmpty(childTag, ICON, xmlEntity.getIcon());
                    setAttributeIfNotEmpty(childTag, TEXT_COLOR, xmlEntity.getTextColor());
                    setAttributeIfNotEmpty(childTag, BACKGROUND_COLOR, xmlEntity.getBackgroundColor());
                    setAttributeIfNotEmpty(childTag, STRIKETHROUGH, xmlEntity.isStrikethroughEnabled() ? "true" : null);
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
                            //类型规则的 path 可能为空，要连 extension 一起比，才不会误删同目录的其他规则
                            if (trimToEmpty(xmlEntity.getPath()).equals(trimToEmpty(tree.getPath()))
                                    && trimToEmpty(xmlEntity.getExtension()).equals(trimToEmpty(tree.getExtension()))) {
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
                setAttributeIfNotEmpty(childTag, PATH, xmlEntity.getPath());
                setAttributeIfNotEmpty(childTag, TITLE, xmlEntity.getTitle());
                setAttributeIfNotEmpty(childTag, EXTENSION, xmlEntity.getExtension());
                setAttributeIfNotEmpty(childTag, PRESENTABLE_TEXT, xmlEntity.getPresentableText());
                setAttributeIfNotEmpty(childTag, TOOLTIP_TITLE, xmlEntity.getTooltipTitle());
                setAttributeIfNotEmpty(childTag, ICON, xmlEntity.getIcon());
                setAttributeIfNotEmpty(childTag, TEXT_COLOR, xmlEntity.getTextColor());
                setAttributeIfNotEmpty(childTag, BACKGROUND_COLOR, xmlEntity.getBackgroundColor());
                setAttributeIfNotEmpty(childTag, STRIKETHROUGH, xmlEntity.isStrikethroughEnabled() ? "true" : null);
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

    /**
     * 写入属性：值非空时写入，为空时把已存在的属性删掉。
     * <p>
     * {@link #tree(XmlTag)} 解析时会把缺失的属性归一化成 ""，若直接回写就会在文件里留下
     * extension=""、icon="" 这类无意义的空属性，因此写入前统一在这里过滤一次。
     * </p>
     */
    private static void setAttributeIfNotEmpty(XmlTag tag, String name, String value) {
        if (value != null && !value.trim().isEmpty()) {
            tag.setAttribute(name, value);
        } else if (null != tag.getAttribute(name)) {
            //传 null 会移除该属性；仅在属性确实存在时调用，新建标签时不做无用操作
            tag.setAttribute(name, null);
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
        XmlAttribute xml_strikethrough = tag.getAttribute(STRIKETHROUGH);
        //只写 extension 的是「全项目按类型」规则，没有 path 也算有效
        if (xml_path != null || xml_extension != null) {
            xmlEntity.setPath(xml_path == null ? null : xml_path.getValue()).setTitle(xml_title == null ? "" : xml_title.getValue()).setExtension(xml_extension == null ? "" : xml_extension.getValue()).setPresentableText(xml_presentable_text == null ? "" : xml_presentable_text.getValue()).setTooltipTitle(xml_tooltip_title == null ? "" : xml_tooltip_title.getValue()).setIcon(xml_icons == null ? "" : xml_icons.getValue()).setTextColor(xml_text_color == null ? "" : xml_text_color.getValue()).setBackgroundColor(xml_background_color == null ? "" : xml_background_color.getValue()).setStrikethrough(xml_strikethrough == null ? null : xml_strikethrough.getValue()).setTag(tag);
            return xmlEntity;
        }
        return null;
    }

    private static String trimToEmpty(String value) {
        return null == value ? "" : value.trim();
    }

}
