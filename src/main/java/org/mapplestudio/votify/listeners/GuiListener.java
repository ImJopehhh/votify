package org.mapplestudio.votify.listeners;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.mapplestudio.votify.Votify;
import org.mapplestudio.votify.gui.PlayerGui;
import org.mapplestudio.votify.gui.RewardEditor;
import org.mapplestudio.votify.gui.RewardTypeSelectGui;
import org.mapplestudio.votify.gui.ServiceRewardEditor;

import java.util.ArrayList;
import java.util.List;

public class GuiListener implements Listener {

    private final Votify plugin = Votify.getInstance();

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        InventoryHolder holder = e.getInventory().getHolder();
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player player = (Player) e.getWhoClicked();

        // 1. RewardEditor Click Handling
        if (holder instanceof RewardEditor) {
            e.setCancelled(true);
            ItemStack clickedItem = e.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

            int slot = e.getRawSlot();
            if (slot == 53 || clickedItem.getType() == Material.EMERALD) {
                plugin.getChatPromptManager().startPrompt(player, "&a&lEnter New Vote Service Name &7(e.g. PlanetMinecraft):", (p, input) -> {
                    String serviceName = input.trim().replace(" ", "_");
                    if (serviceName.isEmpty() || serviceName.contains(".")) {
                        p.sendMessage(ChatColor.RED + "Invalid service name! Cannot contain dots or be empty.");
                        return;
                    }
                    if (plugin.getVoteRewardsConfig().contains("rewards." + serviceName)) {
                        p.sendMessage(ChatColor.RED + "A service with that name already exists!");
                        new RewardEditor(plugin).openInventory(p);
                        return;
                    }
                    plugin.getVoteRewardsConfig().set("rewards." + serviceName, new ArrayList<String>());
                    plugin.saveVoteRewardsConfig();
                    p.sendMessage(ChatColor.GREEN + "Service '" + serviceName + "' created! Opening editor...");
                    new ServiceRewardEditor(plugin, serviceName).openInventory(p);
                });
                return;
            }

            if (clickedItem.getType() == Material.PAPER && clickedItem.hasItemMeta() && clickedItem.getItemMeta().hasDisplayName()) {
                String serviceName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
                new ServiceRewardEditor(plugin, serviceName).openInventory(player);
            }
            return;
        }

        // 2. ServiceRewardEditor Click Handling
        if (holder instanceof ServiceRewardEditor) {
            e.setCancelled(true);
            ServiceRewardEditor editor = (ServiceRewardEditor) holder;
            String serviceName = editor.getServiceName();
            ItemStack clickedItem = e.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

            int slot = e.getRawSlot();

            // Go Back
            if (slot == 45 || (clickedItem.getType() == Material.ARROW && clickedItem.getItemMeta() != null && clickedItem.getItemMeta().getDisplayName().contains("Go Back"))) {
                new RewardEditor(plugin).openInventory(player);
                return;
            }

            // Delete Service
            if (slot == 49 || (clickedItem.getType() == Material.BARRIER && clickedItem.getItemMeta() != null && clickedItem.getItemMeta().getDisplayName().contains("Delete Service"))) {
                if (e.isRightClick()) {
                    if (serviceName.equalsIgnoreCase("default")) {
                        player.sendMessage(ChatColor.RED + "The 'default' service cannot be deleted!");
                        return;
                    }
                    plugin.getVoteRewardsConfig().set("rewards." + serviceName, null);
                    plugin.saveVoteRewardsConfig();
                    player.sendMessage(ChatColor.GREEN + "Service '" + serviceName + "' has been successfully deleted.");
                    new RewardEditor(plugin).openInventory(player);
                } else {
                    player.sendMessage(ChatColor.YELLOW + "Please &cRight-Click&e on the barrier to confirm deleting service: &f" + serviceName);
                }
                return;
            }

            // Add New Reward
            if (slot == 53 || (clickedItem.getType() == Material.EMERALD && clickedItem.getItemMeta() != null && clickedItem.getItemMeta().getDisplayName().contains("Add New Reward"))) {
                new RewardTypeSelectGui(plugin, player, serviceName).open();
                return;
            }

            // Reward Item Slot (0-44)
            if (slot >= 0 && slot < 45) {
                if (e.isRightClick()) {
                    List<String> rewards = plugin.getVoteRewardsConfig().getStringList("rewards." + serviceName);
                    if (slot < rewards.size()) {
                        String removed = rewards.remove(slot);
                        plugin.getVoteRewardsConfig().set("rewards." + serviceName, rewards);
                        plugin.saveVoteRewardsConfig();
                        player.sendMessage(ChatColor.GREEN + "Deleted reward #" + (slot + 1) + " (" + removed + ")");
                        new ServiceRewardEditor(plugin, serviceName).openInventory(player);
                    }
                }
            }
            return;
        }

        // 3. RewardTypeSelectGui Click Handling
        if (holder instanceof RewardTypeSelectGui) {
            e.setCancelled(true);
            RewardTypeSelectGui selectGui = (RewardTypeSelectGui) holder;
            String serviceName = selectGui.getServiceName();
            int slot = e.getRawSlot();

            // Back
            if (slot == 22) {
                new ServiceRewardEditor(plugin, serviceName).openInventory(player);
                return;
            }

            // Command Reward
            if (slot == 11) {
                plugin.getChatPromptManager().startPrompt(player, "&e&lEnter Console Command &7(use %player% placeholder):", (p, input) -> {
                    String cmd = input.startsWith("/") ? input.substring(1) : input;
                    List<String> list = plugin.getVoteRewardsConfig().getStringList("rewards." + serviceName);
                    list.add("command: " + cmd);
                    plugin.getVoteRewardsConfig().set("rewards." + serviceName, list);
                    plugin.saveVoteRewardsConfig();
                    p.sendMessage(ChatColor.GREEN + "Added command reward: " + cmd);
                    new ServiceRewardEditor(plugin, serviceName).openInventory(p);
                });
                return;
            }

            // Message Reward
            if (slot == 13) {
                plugin.getChatPromptManager().startPrompt(player, "&a&lEnter Message Reward &7(color codes like &a supported):", (p, input) -> {
                    List<String> list = plugin.getVoteRewardsConfig().getStringList("rewards." + serviceName);
                    list.add("message: " + input);
                    plugin.getVoteRewardsConfig().set("rewards." + serviceName, list);
                    plugin.saveVoteRewardsConfig();
                    p.sendMessage(ChatColor.GREEN + "Added message reward: " + input);
                    new ServiceRewardEditor(plugin, serviceName).openInventory(p);
                });
                return;
            }

            // Item Reward
            if (slot == 15) {
                if (e.isLeftClick()) {
                    ItemStack hand = player.getInventory().getItemInMainHand();
                    if (hand == null || hand.getType() == Material.AIR) {
                        player.sendMessage(ChatColor.RED + "You are not holding any item in your main hand!");
                        return;
                    }
                    String itemString = RewardTypeSelectGui.serializeItem(hand);
                    List<String> list = plugin.getVoteRewardsConfig().getStringList("rewards." + serviceName);
                    list.add(itemString);
                    plugin.getVoteRewardsConfig().set("rewards." + serviceName, list);
                    plugin.saveVoteRewardsConfig();
                    player.sendMessage(ChatColor.GREEN + "Added item reward: " + itemString);
                    new ServiceRewardEditor(plugin, serviceName).openInventory(player);
                } else if (e.isRightClick()) {
                    plugin.getChatPromptManager().startPrompt(player, "&6&lEnter Item Reward String &7(e.g. DIAMOND 5 name:&bLucky_Diamond):", (p, input) -> {
                        String itemString = input.toLowerCase().startsWith("item:") ? input : "item: " + input;
                        List<String> list = plugin.getVoteRewardsConfig().getStringList("rewards." + serviceName);
                        list.add(itemString);
                        plugin.getVoteRewardsConfig().set("rewards." + serviceName, list);
                        plugin.saveVoteRewardsConfig();
                        p.sendMessage(ChatColor.GREEN + "Added item reward: " + itemString);
                        new ServiceRewardEditor(plugin, serviceName).openInventory(p);
                    });
                }
                return;
            }
            return;
        }

        // 4. PlayerGui Click Handling
        ItemStack clickedItem = e.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        if (holder instanceof PlayerGui) {
            e.setCancelled(true);
            PlayerGui gui = (PlayerGui) holder;

            if (gui.getType() == PlayerGui.GuiType.MAIN) {
                if (clickedItem.getType() == Material.BEACON) {
                    new PlayerGui(plugin, player, PlayerGui.GuiType.SITES).open();
                } else if (clickedItem.getType() == Material.PLAYER_HEAD && clickedItem.getItemMeta() != null && clickedItem.getItemMeta().getDisplayName().contains("Your Stats")) {
                    new PlayerGui(plugin, player, PlayerGui.GuiType.STATS).open();
                } else if (clickedItem.getType() == Material.GOLD_INGOT) {
                    new PlayerGui(plugin, player, PlayerGui.GuiType.LEADERBOARD).open();
                }
            } else if (gui.getType() == PlayerGui.GuiType.SITES) {
                if (clickedItem.getType() == Material.ARROW && clickedItem.getItemMeta() != null && clickedItem.getItemMeta().getDisplayName().contains("Back")) {
                    new PlayerGui(plugin, player, PlayerGui.GuiType.MAIN).open();
                    return;
                }

                ConfigurationSection sitesSec = plugin.getConfig().getConfigurationSection("vote-sites");
                if (sitesSec != null && clickedItem.hasItemMeta() && clickedItem.getItemMeta().hasDisplayName()) {
                    String itemName = clickedItem.getItemMeta().getDisplayName();
                    for (String key : sitesSec.getKeys(false)) {
                        String siteName = org.mapplestudio.votify.util.ColorUtil.colorize(sitesSec.getString(key + ".name", key));
                        if (itemName.equals(siteName)) {
                            String url = sitesSec.getString(key + ".url", "");
                            player.closeInventory();
                            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);

                            net.md_5.bungee.api.chat.TextComponent msg = new net.md_5.bungee.api.chat.TextComponent(
                                    org.mapplestudio.votify.util.ColorUtil.colorize("&bVote link for " + siteName + "&7: "));
                            net.md_5.bungee.api.chat.TextComponent linkBtn = new net.md_5.bungee.api.chat.TextComponent(
                                    org.mapplestudio.votify.util.ColorUtil.colorize("&a&l[CLICK TO OPEN LINK]"));
                            linkBtn.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                                    net.md_5.bungee.api.chat.ClickEvent.Action.OPEN_URL, url));
                            linkBtn.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                                    net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                                    new net.md_5.bungee.api.chat.ComponentBuilder(org.mapplestudio.votify.util.ColorUtil.colorize("&bClick to open:\n&f" + url)).create()));
                            msg.addExtra(linkBtn);
                            player.spigot().sendMessage(msg);
                            return;
                        }
                    }
                }
            } else if (gui.getType() == PlayerGui.GuiType.STATS || gui.getType() == PlayerGui.GuiType.LEADERBOARD) {
                if (clickedItem.getType() == Material.ARROW && clickedItem.getItemMeta() != null && clickedItem.getItemMeta().getDisplayName().contains("Back")) {
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

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent e) {
        InventoryHolder holder = e.getInventory().getHolder();
        if (holder instanceof PlayerGui || holder instanceof RewardEditor || holder instanceof ServiceRewardEditor || holder instanceof RewardTypeSelectGui) {
            e.setCancelled(true);
        }
    }
}
