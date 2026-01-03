package org.mapplestudio.votify.listeners;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
                // Process pending vote rewards
                plugin.getVoteListener().processPendingRewards(event.getPlayer());
                
                // Check for unclaimed top voter rewards
                int rank = plugin.getVoteDataHandler().getUnclaimedRewardRank(event.getPlayer().getUniqueId());
                if (rank != -1) {
                    String prefix = plugin.getConfig().getString("messages.prefix", "&8[&bVotify&8] &r");
                    event.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&', prefix + "&aYou have an unclaimed Top Voter reward!"));
                    event.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&', prefix + "&eYou were ranked #" + rank + " last month."));
                    event.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&', prefix + "&bType &n/votify claim&b to claim it now!"));
                }
            }
        }, delay * 20L); // Convert seconds to ticks
    }
}
