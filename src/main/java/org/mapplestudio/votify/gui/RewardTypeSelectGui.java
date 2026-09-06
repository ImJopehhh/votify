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

public class RewardTypeSelectGui implements InventoryHolder {

    private final Votify plugin;
    private final Player player;
    private final String serviceName;
    private final Inventory inv;

    public RewardTypeSelectGui(Votify plugin, Player player, String serviceName) {
        this.plugin = plugin;
        this.player = player;
        this.serviceName = serviceName;
        this.inv = Bukkit.createInventory(this, 27, ChatColor.DARK_BLUE + "Add Reward: " + serviceName);
        initializeItems();
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    public String getServiceName() {
        return serviceName;
    }

    private void initializeItems() {
        inv.clear();
        ItemStack filler = createGuiItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }

        // Command Option
        inv.setItem(11, createGuiItem(Material.COMMAND_BLOCK, "&e&lCommand Reward",
                "&7Executes a console command.",
                "&7Placeholders: &e%player%",
                "&8Example: eco give %player% 500",
                "",
                "&bClick to enter command via chat"));

        // Message Option
        inv.setItem(13, createGuiItem(Material.WRITABLE_BOOK, "&a&lMessage Reward",
                "&7Sends a private message to voter.",
                "&7Supports & color codes.",
                "&8Example: &aThanks for voting!",
                "",
                "&bClick to enter message via chat"));

        // Item Option
        inv.setItem(15, createGuiItem(Material.CHEST, "&6&lItem Reward",
                "&7Gives an item to the player.",
                "",
                "&eLeft-Click: &fUse item in your main hand",
                "&eRight-Click: &fType format string in chat"));

        // Back Option
        inv.setItem(22, createGuiItem(Material.ARROW, "&cBack", "&7Return to reward editor."));
    }

    private ItemStack createGuiItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            List<String> loreList = new ArrayList<>();
            for (String line : lore) {
                loreList.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            meta.setLore(loreList);
            item.setItemMeta(meta);
        }
        return item;
    }

    public void open() {
        player.openInventory(inv);
    }

    public static String serializeItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;
        StringBuilder sb = new StringBuilder();
        sb.append("item: ").append(item.getType().name()).append(" ").append(item.getAmount());

        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                if (meta.hasDisplayName()) {
                    String name = meta.getDisplayName().replace('§', '&').replace(' ', '_');
                    sb.append(" name:").append(name);
                }
                if (meta.hasLore() && meta.getLore() != null && !meta.getLore().isEmpty()) {
                    List<String> lore = meta.getLore();
                    List<String> formatted = new ArrayList<>();
                    for (String line : lore) {
                        formatted.add(line.replace('§', '&').replace(' ', '_'));
                    }
                    sb.append(" lore:").append(String.join("|", formatted));
                }
                if (meta.hasEnchants()) {
                    for (java.util.Map.Entry<org.bukkit.enchantments.Enchantment, Integer> entry : meta.getEnchants().entrySet()) {
                        String enchantKey = entry.getKey().getKey().getKey();
                        sb.append(" enchant:").append(enchantKey).append(":").append(entry.getValue());
                    }
                }
                if (meta.hasCustomModelData()) {
                    sb.append(" custommodeldata:").append(meta.getCustomModelData());
                }
            }
        }
        return sb.toString();
    }
}
