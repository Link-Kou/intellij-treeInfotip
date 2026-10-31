package com.plugins.infotip.storage;

import java.util.ArrayList;
import java.util.List;

/**
 * 嵌套路径的拼接与归一
 *
 * <p>
 * V7 的 {@code <node path="...">} 写的是<b>相对上一层</b>的那一截，而内存里的
 * {@link XmlEntity#getPath()} 一律是完整路径，两者之间的换算全在这个类里。纯字符串运算、
 * 不碰平台 API，要验证可以照 CLAUDE.md 说的抄成单文件 Java 跑断言。
 * </p>
 * <p>
 * 约定：完整路径以 {@code /} 开头、末尾不带 {@code /}，项目根目录本身是空串。这和 6.x 写在
 * {@code path} 属性里的形式一致，所以 {@code TreesUtils} 那边的匹配逻辑不用动。
 * </p>
 */
public class NodePaths {

    private NodePaths() {
    }

    /**
     * 把一截相对路径接到父路径后面
     *
     * <p>
     * 两边多写的斜杠都容忍：用户手写 {@code path="/main/"} 和 {@code path="main"} 应该是一个意思。
     * 反斜杠也归一成正斜杠，Windows 上手改配置时容易顺手写成反的。
     * </p>
     *
     * @param parent   父节点的完整路径，根节点传空串
     * @param fragment 本层写的那一截，可空（表示就是父节点这个路径）
     * @return 完整路径，以 / 开头；父子都空时返回空串
     */
    public static String join(String parent, String fragment) {
        final String head = trimTrailingSlash(parent);
        final String tail = trimSlashes(fragment);
        if (tail.isEmpty()) {
            return head;
        }
        return head + "/" + tail;
    }

    /**
     * 完整路径减去父路径，得到本层该写进 {@code path} 属性的那一截
     *
     * <p>
     * <b>必须卡在 {@code /} 上</b>，不能用裸的 {@code startsWith}：{@code /a/CarrierRecruit} 和
     * {@code /a/CarrierRecruitReg} 是字符串前缀关系，裸比会把后者算成前者底下的
     * {@code Reg}，路径当场就错了。这个坑 6.x 的 {@code stripPrefix} 踩过一次。
     * </p>
     *
     * @return 剩下的那截（不带首尾斜杠）；完整路径正好等于父路径时返回空串；
     * 不在父路径底下时返回 {@code null}
     */
    public static String relativize(String fullPath, String parent) {
        final String full = trimTrailingSlash(fullPath);
        final String head = trimTrailingSlash(parent);
        if (head.isEmpty()) {
            return trimSlashes(full);
        }
        if (full.equals(head)) {
            return "";
        }
        if (full.startsWith(head + "/")) {
            return full.substring(head.length() + 1);
        }
        return null;
    }

    /**
     * 拆成一段段目录名，空段（连写的斜杠）直接丢掉
     */
    public static List<String> segments(String path) {
        final List<String> result = new ArrayList<>();
        for (String piece : trimSlashes(path).split("/")) {
            if (!piece.isEmpty()) {
                result.add(piece);
            }
        }
        return result;
    }

    /**
     * 去掉末尾多写的斜杠。只写 {@code /} 的等于项目根目录，归一成空串
     */
    public static String trimTrailingSlash(String path) {
        String result = normalizeSeparators(path);
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static String trimSlashes(String path) {
        String result = trimTrailingSlash(path);
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        return result;
    }

    private static String normalizeSeparators(String path) {
        return null == path ? "" : path.trim().replace('\\', '/');
    }
}
