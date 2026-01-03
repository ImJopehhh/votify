package org.mapplestudio.votify.listeners;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.mapplestudio.votify.Votify;
import org.mapplestudio.votify.gui.PlayerGui;

public class GuiListener implements Listener {

    private final Votify plugin = Votify.getInstance();

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        InventoryHolder holder = e.getInventory().getHolder();
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player player = (Player) e.getWhoClicked();
        ItemStack clickedItem = e.getCurrentItem();

        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        if (holder instanceof PlayerGui) {
            e.setCancelled(true);
            PlayerGui gui = (PlayerGui) holder;

            if (gui.getType() == PlayerGui.GuiType.MAIN) {
                if (clickedItem.getType() == Material.PLAYER_HEAD && clickedItem.getItemMeta().getDisplayName().contains("Your Stats")) {
                    new PlayerGui(plugin, player, PlayerGui.GuiType.STATS).open();
                } else if (clickedItem.getType() == Material.GOLD_INGOT) {
                    new PlayerGui(plugin, player, PlayerGui.GuiType.LEADERBOARD).open();
                }
            } else if (gui.getType() == PlayerGui.GuiType.STATS || gui.getType() == PlayerGui.GuiType.LEADERBOARD) {
                if (clickedItem.getType() == Material.ARROW && clickedItem.getItemMeta().getDisplayName().contains("Back")) {
                    new PlayerGui(plugin, player, PlayerGui.GuiType.MAIN).open();
                }
            } else if (gui.getType() == PlayerGui.GuiType.CLAIM) {
                if (clickedItem.getType() == Material.EMERALD) {
                    boolean success = plugin.getVoteDataHandler().claimReward(player.getUniqueId());
                    if (success) {
                        player.closeInventory();
                        player.sendMessage(ChatColor.GREEN + "Reward claimed successfully!");
                    } else {
                        player.closeInventory();
                        player.sendMessage(ChatColor.RED + "Failed to claim reward! Your inventory might be full.");
                        player.sendMessage(ChatColor.RED + "Please clear some space and try again.");
                    }
                }
            }
        }
    }
}
