package org.mapplestudio.votify.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.mapplestudio.votify.Votify;

import java.util.ArrayList;
import java.util.List;

public class ServiceRewardEditor implements InventoryHolder {

    private final Votify plugin;
    private final Inventory inv;
    private final String serviceName;

    public ServiceRewardEditor(Votify plugin, String serviceName) {
        this.plugin = plugin;
        this.serviceName = serviceName;
        this.inv = Bukkit.createInventory(this, 54, "Editing: " + serviceName);
        initializeItems();
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    public void initializeItems() {
        inv.clear();
        List<String> rewards = plugin.getVoteRewardsConfig().getStringList("rewards." + serviceName);

        for (String reward : rewards) {
            String[] parts = reward.split(":", 2);
            String type = parts[0];
            String value = parts.length > 1 ? parts[1].trim() : "";
            Material material;
            switch (type.toLowerCase()) {
                case "command":
                    material = Material.COMMAND_BLOCK;
                    break;
                case "message":
                    material = Material.WRITABLE_BOOK;
                    break;
                case "item":
                    material = Material.CHEST;
                    break;
                default:
                    material = Material.BARRIER;
            }
            inv.addItem(createGuiItem(material, "&e" + type.toUpperCase(), "&b" + value, "", "&cRight-click to delete"));
        }

        inv.setItem(45, createGuiItem(Material.ARROW, "&cGo Back", "&7Return to the main editor."));
        inv.setItem(53, createGuiItem(Material.EMERALD, "&aAdd New Reward", "&7Click to add a new reward."));
    }

    protected ItemStack createGuiItem(final Material material, final String name, final String... lore) {
        final ItemStack item = new ItemStack(material, 1);
        final ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        List<String> loreList = new ArrayList<>();
        for (String line : lore) {
            loreList.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        meta.setLore(loreList);
        item.setItemMeta(meta);
        return item;
    }

    public void openInventory(Player p) {
        p.openInventory(inv);
    }

    public String getServiceName() {
        return serviceName;
    }
}
