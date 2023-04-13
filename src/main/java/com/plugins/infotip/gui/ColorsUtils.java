package com.plugins.infotip.gui;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * A <code>Icons</code> Class
 *
 * @author lk
 * @version 1.0
 * @date 2021/6/18 19:59
 */
public class ColorsUtils {

    public final static String COLOR_TEXT_COLOR_NAME = "TEXTCOLOR";
    public final static String COLOR_BACKGROUND_COLOR = "BACKGROUNDCOLOR";


    public static String toRBGStr(Color color) {
        if (null == color) {
            return null;
        }
        return String.format("%s,%s,%s", color.getRed(), color.getGreen(), color.getBlue());
    }

    public static Color toColor(String rgb) {
        if (null != rgb) {
            final String[] split = rgb.split(",");
            if (split.length == 3) {
                return new Color(Integer.parseInt(split[0]), Integer.parseInt(split[1]), Integer.parseInt(split[2]));
            }
        }
        return null;
    }

}
