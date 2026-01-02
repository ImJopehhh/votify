package org.mapplestudio.muVoter.listeners;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.mapplestudio.muVoter.MuVoter;
import org.mapplestudio.muVoter.gui.RewardEditor;
import org.mapplestudio.muVoter.gui.ServiceRewardEditor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GuiListener implements Listener {

    private final MuVoter plugin = MuVoter.getInstance();
    private final Map<UUID, String> playerInputMode = new HashMap<>(); // UUID -> "add_service" or "add_reward:serviceName"

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        InventoryHolder holder = e.getInventory().getHolder();
        Player player = (Player) e.getWhoClicked();
        ItemStack clickedItem = e.getCurrentItem();

        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        if (holder instanceof RewardEditor) {
            e.setCancelled(true);
            if (clickedItem.getType() == Material.EMERALD && clickedItem.getItemMeta().getDisplayName().contains("Add New Service")) {
                player.closeInventory();
                player.sendMessage(ChatColor.GREEN + "Please type the new service name in chat (e.g., 'my-vote-site.com'). Type 'cancel' to abort.");
                playerInputMode.put(player.getUniqueId(), "add_service");
            } else if (clickedItem.getType() == Material.PAPER) {
                String serviceName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
                ServiceRewardEditor serviceGui = new ServiceRewardEditor(plugin, serviceName);
                serviceGui.openInventory(player);
            }
        } else if (holder instanceof ServiceRewardEditor) {
            e.setCancelled(true);
            ServiceRewardEditor serviceEditor = (ServiceRewardEditor) holder;
            String serviceName = serviceEditor.getServiceName();

            if (clickedItem.getType() == Material.ARROW) { // Go Back
                RewardEditor mainGui = new RewardEditor(plugin);
                mainGui.openInventory(player);
            } else if (clickedItem.getType() == Material.EMERALD) { // Add New Reward
                player.closeInventory();
                player.sendMessage(ChatColor.GREEN + "Enter the new reward in chat (e.g., 'command:eco give %player% 100'). Type 'cancel' to abort.");
                playerInputMode.put(player.getUniqueId(), "add_reward:" + serviceName);
            } else if (e.getClick() == ClickType.RIGHT) { // Delete Reward
                List<String> rewards = plugin.getVoteRewardsConfig().getStringList("rewards." + serviceName);
                String rewardLore = ChatColor.stripColor(clickedItem.getItemMeta().getLore().get(0));
                rewards.removeIf(r -> r.contains(rewardLore));
                plugin.getVoteRewardsConfig().set("rewards." + serviceName, rewards);
                plugin.saveVoteRewardsConfig();
                serviceEditor.initializeItems(); // Refresh GUI
                player.sendMessage(ChatColor.GREEN + "Reward deleted.");
            }
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent e) {
        Player player = e.getPlayer();
        UUID playerUUID = player.getUniqueId();

        if (playerInputMode.containsKey(playerUUID)) {
            e.setCancelled(true);
            String input = e.getMessage();
            String mode = playerInputMode.get(playerUUID);

            // Run on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (input.equalsIgnoreCase("cancel")) {
                    player.sendMessage(ChatColor.RED + "Operation cancelled.");
                    playerInputMode.remove(playerUUID);
                    return;
                }

                if (mode.equals("add_service")) {
                    plugin.getVoteRewardsConfig().set("rewards." + input, new ArrayList<String>());
                    plugin.saveVoteRewardsConfig();
                    player.sendMessage(ChatColor.GREEN + "Service '" + input + "' added. Opening editor...");
                    RewardEditor mainGui = new RewardEditor(plugin);
                    mainGui.openInventory(player);
                } else if (mode.startsWith("add_reward:")) {
                    String serviceName = mode.split(":")[1];
                    List<String> rewards = plugin.getVoteRewardsConfig().getStringList("rewards." + serviceName);
                    rewards.add(input);
                    plugin.getVoteRewardsConfig().set("rewards." + serviceName, rewards);
                    plugin.saveVoteRewardsConfig();
                    player.sendMessage(ChatColor.GREEN + "Reward added. Opening editor...");
                    ServiceRewardEditor serviceGui = new ServiceRewardEditor(plugin, serviceName);
                    serviceGui.openInventory(player);
                }

                playerInputMode.remove(playerUUID);
            });
        }
    }
}
