package com.plugins.infotip.ui;

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
public class Colors {

    private Color textcolor;
    private Color backgroundcolor;


    public static String getColorSting(Map<String, Color> colorMap) {
        //String.format("textcolor:%s|forcedtextcolor:%s|backgroundcolor:%s")
        final Iterator<Map.Entry<String, Color>> iterator = colorMap.entrySet().iterator();
        List<String> colorlist = new ArrayList<String>();
        while (iterator.hasNext()) {
            final Map.Entry<String, Color> next = iterator.next();
            if (null != next) {
                colorlist.add(String.format("%s:%s", next.getKey(), toRBGStr(next.getValue())));
            }
        }
        if (colorlist.size() > 0) {
            return String.join(";", colorlist);
        }
        return null;
    }

    public static String toRBGStr(Color color) {
        if (null == color) {
            return null;
        }
        return String.format("%s,%s,%s", color.getRed(), color.getGreen(), color.getBlue());
    }

    public static Map<String, Color> toMapColor(String color) {
        if (null == color) {
            return null;
        }
        final String[] split = color.split(";");
        if (split.length > 0) {
            Map<String, Color> colors = new HashMap<String, Color>();
            for (String s : split) {
                final String[] split1 = s.split(":");
                colors.put(split1[0], toColor(split1[1]));
            }
            return colors;
        }
        return null;
    }

    public static Colors toColors(String color) {
        if (null == color) {
            return null;
        }
        final String[] split = color.split(";");
        if (split.length > 0) {
            Colors colors = new Colors();
            for (String s : split) {
                final String[] split1 = s.split(":");
                switch (split1[0]) {
                    case "textcolor":
                        colors.setTextcolor(toColor(split1[1]));
                        break;
                    case "backgroundcolor":
                        colors.setBackgroundcolor(toColor(split1[1]));
                        break;
                    default:
                        break;
                }
            }
            return colors;
        }
        return null;
    }

    public static Color toColor(String rgb) {
        final String[] split = rgb.split(",");
        return new Color(Integer.parseInt(split[0]), Integer.parseInt(split[1]), Integer.parseInt(split[2]));
    }

    //region getst
    public Color getTextcolor() {
        return textcolor;
    }

    public Colors setTextcolor(Color textcolor) {
        this.textcolor = textcolor;
        return this;
    }

    public Color getBackgroundcolor() {
        return backgroundcolor;
    }

    public Colors setBackgroundcolor(Color backgroundcolor) {
        this.backgroundcolor = backgroundcolor;
        return this;
    }
    //endregion
}
