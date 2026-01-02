package org.mapplestudio.muVoter.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.mapplestudio.muVoter.MuVoter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class RewardEditor implements InventoryHolder {

    private final MuVoter plugin;
    private final Inventory inv;

    public RewardEditor(MuVoter plugin) {
        this.plugin = plugin;
        this.inv = Bukkit.createInventory(this, 54, "Vote Reward Editor");
        initializeItems();
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    public void initializeItems() {
        inv.clear();
        ConfigurationSection rewardsSection = plugin.getVoteRewardsConfig().getConfigurationSection("rewards");
        if (rewardsSection != null) {
            Set<String> services = rewardsSection.getKeys(false);
            for (String service : services) {
                List<String> rewards = rewardsSection.getStringList(service);
                inv.addItem(createGuiItem(
                        Material.PAPER,
                        "&e" + service,
                        "&7Click to edit rewards.",
                        "&7Rewards: &b" + rewards.size()
                ));
            }
        }

        // Add a button to add a new service
        inv.setItem(53, createGuiItem(Material.EMERALD, "&aAdd New Service", "&7Click to add a new vote service."));
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
}
