package org.mapplestudio.votify.util;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.mapplestudio.votify.Votify;

public class VotePartyBossBar {

    private final Votify plugin;
    private BossBar bossBar;

    public VotePartyBossBar(Votify plugin) {
        this.plugin = plugin;
        init();
    }

    public void init() {
        cleanup();

        if (!isEnabled()) {
            return;
        }

        BarColor color = BarColor.PURPLE;
        try {
            String colorStr = plugin.getConfig().getString("voteparty.bossbar.color", "PURPLE").toUpperCase();
            color = BarColor.valueOf(colorStr);
        } catch (IllegalArgumentException ignored) {}

        BarStyle style = BarStyle.SOLID;
        try {
            String styleStr = plugin.getConfig().getString("voteparty.bossbar.style", "SOLID").toUpperCase();
            style = BarStyle.valueOf(styleStr);
        } catch (IllegalArgumentException ignored) {}

        String titleTemplate = plugin.getConfig().getString("voteparty.bossbar.title", "&#d946ef&lVOTE PARTY: &e%current%&7/&6%required% votes &8(&b%percent%%&8)");
        bossBar = Bukkit.createBossBar(ColorUtil.colorize(titleTemplate), color, style);

        for (Player player : Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(player);
        }

        update();
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("voteparty.bossbar.enabled", false);
    }

    public void update() {
        if (bossBar == null || !isEnabled()) return;
        int current = plugin.getVoteDataHandler().getVotePartyCurrent();
        int required = plugin.getVoteDataHandler().getVotePartyRequired();
        update(current, required);
    }

    public void update(int current, int required) {
        if (bossBar == null || !isEnabled()) return;

        double progress = required > 0 ? (double) current / (double) required : 0.0;
        bossBar.setProgress(Math.min(1.0, Math.max(0.0, progress)));

        int percent = (int) (progress * 100);
        String titleTemplate = plugin.getConfig().getString("voteparty.bossbar.title", "&#d946ef&lVOTE PARTY: &e%current%&7/&6%required% votes &8(&b%percent%%&8)");
        String formatted = titleTemplate.replace("%current%", String.valueOf(current))
                                        .replace("%required%", String.valueOf(required))
                                        .replace("%percent%", String.valueOf(percent));
        bossBar.setTitle(ColorUtil.colorize(formatted));
    }

    public void addPlayer(Player player) {
        if (bossBar != null && isEnabled()) {
            bossBar.addPlayer(player);
        }
    }

    public void removePlayer(Player player) {
        if (bossBar != null) {
            bossBar.removePlayer(player);
        }
    }

    public void cleanup() {
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
    }
}
