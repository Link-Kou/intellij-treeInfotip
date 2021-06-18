package com.plugins.infotip;

import com.plugins.infotip.ui.Icons;

import javax.swing.*;
import java.lang.reflect.Field;
import java.util.ArrayList;

/**
 * A <code>FileIcons</code> Class
 *
 * @author lk
 * @version 1.0
 * @date 2021/6/18 20:09
 */
public class FileIcons {

    private static final ArrayList<Icons> icons = new ArrayList<>();

    static {
        ArrayList<Class<?>> classArrayList = new ArrayList<>();
        try {
            Class<?> aClass1 = Class.forName(getNames());
            Class<?>[] classes = aClass1.getClasses();
            getClasss(classes, classArrayList);
            for (Class<?> aClass : classArrayList) {
                Field[] fields = aClass.getFields();
                for (Field field : fields) {
                    field.setAccessible(true);
                    if (field.getType().equals(Icon.class)) {
                        Icon icon = (Icon) field.get(null);
                        int length1 = aClass.getName().length();
                        int length2 = getNames().length();
                        if (length1 > length2) {
                            String substring = aClass.getName().substring(length2, length1).replaceAll("\\$", "");
                            icons.add(new Icons()
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

    public static ArrayList<Icons> getAllIcons() {
        return icons;
    }

    private static String getNames() {
        return "com.intellij.icons.AllIcons";
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
