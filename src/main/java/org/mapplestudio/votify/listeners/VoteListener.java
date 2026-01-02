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

    @EventHandler
    public void onVotifierEvent(VotifierEvent event) {
        Vote vote = event.getVote();
        String username = vote.getUsername();
        String serviceName = vote.getServiceName();

        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("Received a vote for " + username + " from " + serviceName);
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            // Use getOfflinePlayer to handle both online and offline players initially
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(username);
            
            // If the player has never played before, offlinePlayer.getName() might be null or different.
            // However, Votifier usually sends the correct username.
            if (offlinePlayer.getName() == null && username != null) {
                 // Fallback if Bukkit can't resolve the name yet (rare)
                 plugin.getLogger().warning("Could not resolve player name for vote from: " + username);
            }

            processVote(offlinePlayer, serviceName, username);
        });
    }

    public void processVote(OfflinePlayer offlinePlayer, String serviceName, String username) {
        // Use the username from the vote if the offline player name is null (though unlikely if they joined before)
        String playerName = offlinePlayer.getName() != null ? offlinePlayer.getName() : username;

        plugin.getVoteDataHandler().addVote(offlinePlayer.getUniqueId(), serviceName);
        
        // Execute rewards on the main thread because Bukkit API (dispatchCommand, inventory) requires it
        Bukkit.getScheduler().runTask(plugin, () -> {
             executeRewards(offlinePlayer, serviceName, playerName);
        });

        if (offlinePlayer.isOnline()) {
            Player player = offlinePlayer.getPlayer();
            if (player != null) {
                String prefix = plugin.getConfig().getString("messages.prefix", "&8[&bVotify&8] &r");
                String message = plugin.getConfig().getString("messages.vote-received", "%prefix% &aThanks, &e%player%&a, for voting on &e%service%&a!");
                
                // Replace placeholders
                message = message.replace("%prefix%", prefix)
                                 .replace("%player%", playerName)
                                 .replace("%service%", serviceName);
                
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
            }
        }
    }

    private void executeRewards(OfflinePlayer offlinePlayer, String serviceName, String playerName) {
        ConfigurationSection rewardsConfig = plugin.getVoteRewardsConfig().getConfigurationSection("rewards");
        if (rewardsConfig == null) {
            plugin.getLogger().warning("No 'rewards' section found in voterewards.yml!");
            return;
        }

        List<String> rewards;
        // Check if the specific service exists in the config
        if (rewardsConfig.contains(serviceName)) {
            rewards = rewardsConfig.getStringList(serviceName);
        } else {
            // If not found, try replacing dots with underscores as a fallback
            String safeServiceName = serviceName.replace(".", "_");
             if (rewardsConfig.contains(safeServiceName)) {
                rewards = rewardsConfig.getStringList(safeServiceName);
            } else {
                // Fallback to default rewards
                rewards = rewardsConfig.getStringList("default");
            }
        }

        for (String rewardString : rewards) {
            // Replace %player% with the actual player name
            rewardString = rewardString.replace("%player%", playerName);
            
            String[] parts = rewardString.split(":", 2);
            String type = parts[0].trim();
            String value = parts.length > 1 ? parts[1].trim() : "";

            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("Processing reward: Type=" + type + ", Value=" + value);
            }

            switch (type.toLowerCase()) {
                case "command":
                    // Dispatch command from console
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), value);
                    break;
                case "message":
                    if (offlinePlayer.isOnline() && offlinePlayer.getPlayer() != null) {
                        String prefix = plugin.getConfig().getString("messages.prefix", "&8[&bVotify&8] &r");
                        value = value.replace("%prefix%", prefix);
                        offlinePlayer.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&', value));
                    }
                    break;
                case "item":
                    if (offlinePlayer.isOnline() && offlinePlayer.getPlayer() != null) {
                        giveItem(offlinePlayer.getPlayer(), value);
                    }
                    break;
                default:
                    plugin.getLogger().warning("Unknown reward type: " + type + " in reward string: " + rewardString);
            }
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
                // Not a number, ignore or handle as part of name? 
                // Usually format is ITEM AMOUNT ...
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
        
        // Add item to inventory, drop if full
        player.getInventory().addItem(item).forEach((index, remainingItem) -> {
             player.getWorld().dropItem(player.getLocation(), remainingItem);
             player.sendMessage(ChatColor.RED + "Your inventory was full, so some items were dropped on the ground.");
        });
    }
}
