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
import org.mapplestudio.votify.util.ColorUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

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
        Player onlinePlayer = Bukkit.getPlayerExact(username);
        if (onlinePlayer != null) {
            return onlinePlayer;
        }
        onlinePlayer = Bukkit.getPlayer(username);
        if (onlinePlayer != null) {
            return onlinePlayer;
        }

        // Second, try standard lookup
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(username);
        if (offlinePlayer.hasPlayedBefore()) {
            return offlinePlayer;
        }

        // Third, scan offline players case-insensitively
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (op.getName() != null && op.getName().equalsIgnoreCase(username)) {
                return op;
            }
        }
        
        return offlinePlayer;
    }

    public void processVote(OfflinePlayer offlinePlayer, String serviceName, String username) {
        String playerName = offlinePlayer.getName() != null ? offlinePlayer.getName() : username;

        // 1. Update Stats (Thread-safe config handling in DataHandler)
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
                Bukkit.broadcastMessage(ColorUtil.colorize(broadcastMsg));
            }
            
            // Try to process queue immediately if online
            if (offlinePlayer.isOnline()) {
                processPendingRewards(offlinePlayer.getPlayer());
            }
        });
    }

    private void queueRewards(OfflinePlayer offlinePlayer, String serviceName, String playerName) {
        ConfigurationSection rewardsConfig = plugin.getVoteRewardsConfig().getConfigurationSection("rewards");
        List<String> rewards = new ArrayList<>();

        if (rewardsConfig != null) {
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
        }

        List<String> queuedRewards = new ArrayList<>();
        for (String rewardString : rewards) {
            queuedRewards.add(rewardString.replace("%player%", playerName));
        }

        // 1. Lucky Vote Engine (Chance-based rewards)
        if (plugin.getVoteRewardsConfig().getBoolean("lucky-rewards.enabled", false)) {
            ConfigurationSection tiers = plugin.getVoteRewardsConfig().getConfigurationSection("lucky-rewards.tiers");
            if (tiers != null) {
                double roll = ThreadLocalRandom.current().nextDouble() * 100.0;
                for (String tierKey : tiers.getKeys(false)) {
                    double chance = tiers.getDouble(tierKey + ".chance", 0.0);
                    if (roll <= chance) {
                        List<String> luckyRewards = tiers.getStringList(tierKey + ".rewards");
                        for (String r : luckyRewards) {
                            queuedRewards.add(r.replace("%player%", playerName));
                        }
                        String broadcast = tiers.getString(tierKey + ".broadcast", "");
                        if (broadcast != null && !broadcast.isEmpty()) {
                            String finalBcast = ColorUtil.colorize(broadcast.replace("%player%", playerName));
                            Bukkit.getScheduler().runTask(plugin, () -> Bukkit.broadcastMessage(finalBcast));
                        }
                        break; // Award one lucky tier per roll
                    }
                }
            }
        }

        // 2. Vote Milestones Engine (Cumulative Total Votes Rewards)
        if (plugin.getVoteRewardsConfig().getBoolean("milestones.enabled", false)) {
            ConfigurationSection goals = plugin.getVoteRewardsConfig().getConfigurationSection("milestones.goals");
            if (goals != null) {
                int totalVotes = plugin.getVoteDataHandler().getPlayerStat(offlinePlayer.getUniqueId(), "total");
                for (String goalStr : goals.getKeys(false)) {
                    try {
                        int goal = Integer.parseInt(goalStr);
                        if (totalVotes >= goal && !plugin.getVoteDataHandler().hasClaimedMilestone(offlinePlayer.getUniqueId(), goal)) {
                            plugin.getVoteDataHandler().addClaimedMilestone(offlinePlayer.getUniqueId(), goal);
                            List<String> milestoneRewards = goals.getStringList(goalStr + ".rewards");
                            for (String r : milestoneRewards) {
                                queuedRewards.add(r.replace("%player%", playerName));
                            }
                            String broadcast = goals.getString(goalStr + ".broadcast", "");
                            if (broadcast != null && !broadcast.isEmpty()) {
                                String finalBcast = ColorUtil.colorize(broadcast.replace("%player%", playerName));
                                Bukkit.getScheduler().runTask(plugin, () -> Bukkit.broadcastMessage(finalBcast));
                            }
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        plugin.getVoteDataHandler().addPendingRewards(offlinePlayer.getUniqueId(), queuedRewards);
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
                player.sendMessage(ColorUtil.colorize(value));
                break;
            case "item":
                giveItem(player, value);
                break;
            default:
                plugin.getLogger().warning("Unknown reward type: " + type);
        }
    }

    private void giveItem(Player player, String itemString) {
        String[] parts = itemString.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) return;

        Material material = Material.matchMaterial(parts[0].toUpperCase());
        if (material == null) {
            plugin.getLogger().warning("Invalid material for item reward: " + parts[0]);
            return;
        }

        int amount = 1;
        int startIndex = 1;
        if (parts.length > 1) {
            try {
                amount = Integer.parseInt(parts[1]);
                startIndex = 2;
            } catch (NumberFormatException ignored) {
                // parts[1] is not a number, keep amount at 1 and inspect from index 1
            }
        }

        ItemStack item = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            for (int i = startIndex; i < parts.length; i++) {
                String arg = parts[i];
                String lowerArg = arg.toLowerCase();
                if (lowerArg.startsWith("name:")) {
                    String displayName = arg.substring(5).replace("_", " ");
                    meta.setDisplayName(ColorUtil.colorize(displayName));
                } else if (lowerArg.startsWith("lore:")) {
                    String[] loreLines = arg.substring(5).split("\\|");
                    List<String> lore = new ArrayList<>();
                    for (String line : loreLines) {
                        lore.add(ColorUtil.colorize(line.replace("_", " ")));
                    }
                    meta.setLore(lore);
                } else if (lowerArg.startsWith("enchant:")) {
                    String[] enchantParts = arg.substring(8).split(":");
                    if (enchantParts.length > 0) {
                        String enchantName = enchantParts[0];
                        int level = 1;
                        if (enchantParts.length > 1) {
                            try {
                                level = Integer.parseInt(enchantParts[1]);
                            } catch (NumberFormatException ignored) {}
                        }
                        org.bukkit.enchantments.Enchantment enchantment = org.bukkit.enchantments.Enchantment.getByName(enchantName.toUpperCase());
                        if (enchantment == null) {
                            try {
                                enchantment = org.bukkit.enchantments.Enchantment.getByKey(org.bukkit.NamespacedKey.minecraft(enchantName.toLowerCase()));
                            } catch (Throwable ignored) {}
                        }
                        if (enchantment != null) {
                            meta.addEnchant(enchantment, level, true);
                        } else {
                            plugin.getLogger().warning("Unknown enchantment: " + enchantName);
                        }
                    }
                } else if (lowerArg.startsWith("custommodeldata:")) {
                    try {
                        int cmd = Integer.parseInt(arg.substring(16));
                        meta.setCustomModelData(cmd);
                    } catch (NumberFormatException ignored) {}
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
