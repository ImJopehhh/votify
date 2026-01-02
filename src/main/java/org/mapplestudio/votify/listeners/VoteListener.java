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
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(username);
            plugin.getVoteDataHandler().addVote(offlinePlayer.getUniqueId(), serviceName);
            executeRewards(offlinePlayer, serviceName);

            if (offlinePlayer.isOnline()) {
                Player player = (Player) offlinePlayer;
                String message = plugin.getConfig().getString("messages.vote-received", "&aThanks, &e%player%&a, for voting on &e%service%&a!");
                message = message.replace("%player%", player.getName()).replace("%service%", serviceName);
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.prefix") + " " + message));
            }
        });
    }

    private void executeRewards(OfflinePlayer player, String serviceName) {
        ConfigurationSection rewardsConfig = plugin.getVoteRewardsConfig().getConfigurationSection("rewards");
        if (rewardsConfig == null) {
            plugin.getLogger().warning("No 'rewards' section found in voterewards.yml!");
            return;
        }

        List<String> rewards;
        if (rewardsConfig.contains(serviceName)) {
            rewards = rewardsConfig.getStringList(serviceName);
        } else {
            rewards = rewardsConfig.getStringList("default");
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            for (String rewardString : rewards) {
                rewardString = rewardString.replace("%player%", player.getName());
                String[] parts = rewardString.split(":", 2);
                String type = parts[0];
                String value = parts.length > 1 ? parts[1].trim() : "";

                switch (type.toLowerCase()) {
                    case "command":
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), value);
                        break;
                    case "message":
                        if (player.isOnline()) {
                            ((Player) player).sendMessage(ChatColor.translateAlternateColorCodes('&', value));
                        }
                        break;
                    case "item":
                        if (player.isOnline()) {
                            giveItem((Player) player, value);
                        }
                        break;
                    default:
                        plugin.getLogger().warning("Unknown reward type: " + type);
                }
            }
        });
    }

    private void giveItem(Player player, String itemString) {
        String[] parts = itemString.split(" ");
        Material material = Material.matchMaterial(parts[0]);
        if (material == null) {
            plugin.getLogger().warning("Invalid material for item reward: " + parts[0]);
            return;
        }

        int amount = 1;
        if (parts.length > 1) {
            try {
                amount = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                // Not a number, probably part of the name
            }
        }

        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();

        for (int i = 2; i < parts.length; i++) {
            String arg = parts[i];
            if (arg.startsWith("name:")) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', arg.substring(5).replace("_", " ")));
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
        player.getInventory().addItem(item);
    }
}
