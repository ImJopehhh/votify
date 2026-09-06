package org.mapplestudio.votify.util;

import net.md_5.bungee.api.ChatColor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorUtil {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    /**
     * Translates standard & color codes and modern hex RGB codes (&#RRGGBB)
     * into Minecraft-compatible formatted string.
     */
    public static String colorize(String message) {
        if (message == null) return "";
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            try {
                ChatColor hexColor = ChatColor.of("#" + hex);
                matcher.appendReplacement(buffer, hexColor.toString());
            } catch (Exception e) {
                matcher.appendReplacement(buffer, matcher.group(0));
            }
        }
        matcher.appendTail(buffer);
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    /**
     * Colorizes a list of strings (e.g. item lore or message list).
     */
    public static List<String> colorizeList(List<String> list) {
        if (list == null) return new ArrayList<>();
        List<String> colored = new ArrayList<>();
        for (String s : list) {
            colored.add(colorize(s));
        }
        return colored;
    }
}
