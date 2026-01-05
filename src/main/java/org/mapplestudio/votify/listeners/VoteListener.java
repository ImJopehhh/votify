package org.mapplestudio.votify.listeners;

import com.vexsoftware.votifier.model.Vote;
import com.vexsoftware.votifier.model.VotifierEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.mapplestudio.votify.Votify;

import java.util.ArrayList;
import java.util.List;

public class VoteListener implements Listener {

    private final Votify plugin;

    public VoteListener(Votify plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onVotifierEvent(VotifierEvent event) {
        Vote vote = event.getVote();
        String username = vote.getUsername();
        String serviceName = vote.getServiceName();

        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("Received a vote for " + username + " from " + serviceName);
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            // 1. Try to find player (Case-Insensitive Check for Offline Mode)
            OfflinePlayer offlinePlayer = getOfflinePlayerCaseInsensitive(username);
            
            // 2. Validate Player
            if (offlinePlayer == null || (!offlinePlayer.hasPlayedBefore() && !offlinePlayer.isOnline())) {
                plugin.getLogger().warning("Ignored vote from " + username + " (Player has never joined the server).");
                return;
            }

            processVote(offlinePlayer, serviceName, username);
        });
    }

    // Helper method to find player case-insensitively
    private OfflinePlayer getOfflinePlayerCaseInsensitive(String username) {
        // First, check if player is online (fastest and most accurate)
        Player onlinePlayer = Bukkit.getPlayer(username);
        if (onlinePlayer != null) {
            return onlinePlayer;
        }

        // Second, try standard lookup
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(username);
        if (offlinePlayer.hasPlayedBefore()) {
            return offlinePlayer;
        }
        
        return offlinePlayer;
    }

    public void processVote(OfflinePlayer offlinePlayer, String serviceName, String username) {
        String playerName = offlinePlayer.getName() != null ? offlinePlayer.getName() : username;

        // 1. Update Stats (Thread-safe config handling required in DataHandler)
        plugin.getVoteDataHandler().addVote(offlinePlayer.getUniqueId(), serviceName);
        
        // 2. Queue Rewards
        queueRewards(offlinePlayer, serviceName, playerName);

        // 3. Global Broadcast & Personal Message
        // Must run on main thread for broadcasting
        Bukkit.getScheduler().runTask(plugin, () -> {
            String broadcastMsg = plugin.getConfig().getString("messages.broadcast", "");
            if (broadcastMsg != null && !broadcastMsg.isEmpty()) {
                String prefix = plugin.getConfig().getString("messages.prefix", "&8[&bVotify&8] &r");
                broadcastMsg = broadcastMsg.replace("%prefix%", prefix)
                                           .replace("%player%", playerName)
                                           .replace("%service%", serviceName);
                Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', broadcastMsg));
            }
            
            // Try to process queue immediately if online
            if (offlinePlayer.isOnline()) {
                processPendingRewards(offlinePlayer.getPlayer());
            }
        });
    }

    private void queueRewards(OfflinePlayer offlinePlayer, String serviceName, String playerName) {
        ConfigurationSection rewardsConfig = plugin.getVoteRewardsConfig().getConfigurationSection("rewards");
        if (rewardsConfig == null) return;

        List<String> rewards;
        if (rewardsConfig.contains(serviceName)) {
            rewards = rewardsConfig.getStringList(serviceName);
        } else {
            String safeServiceName = serviceName.replace(".", "_");
             if (rewardsConfig.contains(safeServiceName)) {
                rewards = rewardsConfig.getStringList(safeServiceName);
            } else {
                rewards = rewardsConfig.getStringList("default");
            }
        }

        for (String rewardString : rewards) {
            // Replace %player% here so it's ready for execution
            rewardString = rewardString.replace("%player%", playerName);
            plugin.getVoteDataHandler().addPendingReward(offlinePlayer.getUniqueId(), rewardString);
        }
    }

    public void processPendingRewards(Player player) {
        List<String> pending = plugin.getVoteDataHandler().getPendingRewards(player.getUniqueId());
        if (pending == null || pending.isEmpty()) return;

        // Clear queue first to prevent duplication
        plugin.getVoteDataHandler().clearPendingRewards(player.getUniqueId());

        for (String rewardString : pending) {
            processRewardString(player, rewardString);
        }
    }
    
    public void processRewardString(Player player, String rewardString) {
        String[] parts = rewardString.split(":", 2);
        String type = parts[0].trim();
        String value = parts.length > 1 ? parts[1].trim() : "";

        switch (type.toLowerCase()) {
            case "command":
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), value);
                break;
            case "message":
                String prefix = plugin.getConfig().getString("messages.prefix", "&8[&bVotify&8] &r");
                value = value.replace("%prefix%", prefix);
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', value));
                break;
            case "item":
                giveItem(player, value);
                break;
            default:
                plugin.getLogger().warning("Unknown reward type: " + type);
        }
    }

    private void giveItem(Player player, String itemString) {
        String[] parts = itemString.split(" ");
        if (parts.length == 0) return;

        Material material = Material.matchMaterial(parts[0].toUpperCase());
        if (material == null) {
            plugin.getLogger().warning("Invalid material for item reward: " + parts[0]);
            return;
        }

        int amount = 1;
        if (parts.length > 1) {
            try {
                amount = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
            }
        }

        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            for (int i = 2; i < parts.length; i++) {
                String arg = parts[i];
                if (arg.startsWith("name:")) {
                    String displayName = arg.substring(5).replace("_", " ");
                    meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayName));
                } else if (arg.startsWith("lore:")) {
                    String[] loreLines = arg.substring(5).split("\\|");
                    List<String> lore = new ArrayList<>();
                    for (String line : loreLines) {
                        lore.add(ChatColor.translateAlternateColorCodes('&', line.replace("_", " ")));
                    }
                    meta.setLore(lore);
                }
            }
            item.setItemMeta(meta);
        }
        
        player.getInventory().addItem(item).forEach((index, remainingItem) -> {
             player.getWorld().dropItem(player.getLocation(), remainingItem);
             player.sendMessage(ChatColor.RED + "Your inventory was full, so some items were dropped on the ground.");
        });
    }
}
