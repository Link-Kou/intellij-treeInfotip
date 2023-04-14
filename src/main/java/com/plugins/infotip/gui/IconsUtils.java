package com.plugins.infotip.gui;

import com.intellij.openapi.util.IconLoader;
import com.plugins.infotip.gui.entity.IconEntity;

import javax.swing.*;
import java.lang.reflect.Field;
import java.util.ArrayList;

/**
 * 获取IDEA内默认的所有图标
 *
 * @author lk
 * @version 1.0
 * 2021/6/18 20:09
 */
public class IconsUtils {

    private static final ArrayList<IconEntity> ICONS = new ArrayList<>();

    private static final String classname = "com.intellij.icons.AllIcons";

    public static final Icon MyBatisIcon = IconLoader.getIcon("/icons/mybatis.png", IconsUtils.class);

    static {
        ArrayList<Class<?>> classArrayList = new ArrayList<>();
        try {
            Class<?> aClass1 = Class.forName(classname);
            Class<?>[] classes = aClass1.getClasses();
            getClasss(classes, classArrayList);
            for (Class<?> aClass : classArrayList) {
                Field[] fields = aClass.getFields();
                for (Field field : fields) {
                    field.setAccessible(true);
                    if (field.getType().equals(Icon.class)) {
                        Icon icon = (Icon) field.get(null);
                        int length1 = aClass.getName().length();
                        int length2 = classname.length();
                        if (length1 > length2) {
                            String substring = aClass.getName().substring(length2, length1).replaceAll("\\$", "");
                            ICONS.add(new IconEntity()
                                    .setIcon(icon)
                                    .setName(substring + field.getName()));
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static ArrayList<IconEntity> getAllIcons() {
        return ICONS;
    }

    private static void getClasss(Class<?>[] classes, ArrayList<Class<?>> arrayListclasses) {
        for (Class<?> aClass : classes) {
            Class<?>[] classes1 = aClass.getClasses();
            if (classes1.length > 0) {
                getClasss(classes1, arrayListclasses);
            }
            arrayListclasses.add(aClass);
        }
    }
}
