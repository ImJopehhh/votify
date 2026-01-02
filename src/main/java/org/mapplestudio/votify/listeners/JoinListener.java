package org.mapplestudio.votify.listeners;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.mapplestudio.votify.Votify;

public class JoinListener implements Listener {

    private final Votify plugin;

    public JoinListener(Votify plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        int delay = plugin.getConfig().getInt("login-delay", 5);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline()) {
                plugin.getVoteListener().processPendingRewards(event.getPlayer());
            }
        }, delay * 20L); // Convert seconds to ticks
    }
}
