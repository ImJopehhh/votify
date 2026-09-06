package org.mapplestudio.votify.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.mapplestudio.votify.Votify;
import org.mapplestudio.votify.util.ColorUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PlayerGui implements InventoryHolder {

    private final Votify plugin;
    private final Inventory inv;
    private final Player viewer;
    private final GuiType type;

    public enum GuiType {
        MAIN, STATS, LEADERBOARD, CLAIM, SITES
    }

    public PlayerGui(Votify plugin, Player viewer, GuiType type) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.type = type;
        int size = (type == GuiType.LEADERBOARD) ? 54 : 27;
        this.inv = Bukkit.createInventory(this, size, getTitle(type));
        initializeItems();
    }

    private String getTitle(GuiType type) {
        switch (type) {
            case SITES: return ColorUtil.colorize("&2&lVote Sites");
            case STATS: return ColorUtil.colorize("&3&lYour Statistics");
            case LEADERBOARD: return ColorUtil.colorize("&6&lTop Voters (Monthly)");
            case CLAIM: return ColorUtil.colorize("&a&lClaim Reward");
            default: return ColorUtil.colorize("&9&lVotify Menu");
        }
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    public void initializeItems() {
        inv.clear();
        
        // Fill background
        ItemStack filler = createGuiItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }

        if (type == GuiType.MAIN) {
            // Vote Sites Button (Slot 11)
            inv.setItem(11, createGuiItem(Material.BEACON, "&a&lVote Sites", 
                    "&7Click to view voting links", 
                    "&7and open in browser."));

            // Stats Button (Slot 13)
            inv.setItem(13, createHeadItem(viewer, "&b&lYour Stats", 
                    "&7Click to view your", 
                    "&7voting statistics."));

            // Leaderboard Button (Slot 15)
            inv.setItem(15, createGuiItem(Material.GOLD_INGOT, "&6&lLeaderboard", 
                    "&7Click to view the", 
                    "&7monthly top voters."));

        } else if (type == GuiType.SITES) {
            ConfigurationSection sitesSec = plugin.getConfig().getConfigurationSection("vote-sites");
            if (sitesSec != null) {
                int[] slots = {11, 13, 15, 10, 12, 14, 16};
                int idx = 0;
                for (String key : sitesSec.getKeys(false)) {
                    if (idx >= slots.length) break;
                    String name = sitesSec.getString(key + ".name", key);
                    String matName = sitesSec.getString(key + ".material", "PAPER");
                    Material mat = Material.matchMaterial(matName.toUpperCase());
                    if (mat == null) mat = Material.PAPER;
                    List<String> lore = sitesSec.getStringList(key + ".description");
                    if (lore.isEmpty()) {
                        lore = new ArrayList<>();
                        lore.add("&7Click to open vote link!");
                    }
                    inv.setItem(slots[idx++], createGuiItem(mat, name, lore.toArray(new String[0])));
                }
            }
            inv.setItem(22, createGuiItem(Material.ARROW, "&cBack", "&7Return to main menu"));

        } else if (type == GuiType.STATS) {
            int total = plugin.getVoteDataHandler().getPlayerStat(viewer.getUniqueId(), "total");
            int monthly = plugin.getVoteDataHandler().getPlayerStat(viewer.getUniqueId(), "monthly");
            int wins = plugin.getVoteDataHandler().getPlayerStat(viewer.getUniqueId(), "wins");
            
            // Calculate Rank
            List<Map.Entry<UUID, Integer>> topVoters = plugin.getVoteDataHandler().getTopVoters();
            int rank = -1;
            for (int i = 0; i < topVoters.size(); i++) {
                if (topVoters.get(i).getKey().equals(viewer.getUniqueId())) {
                    rank = i + 1;
                    break;
                }
            }
            String rankStr = (rank != -1) ? "#" + rank : "Unranked";

            inv.setItem(13, createHeadItem(viewer, "&b&l" + viewer.getName(), 
                    "&7Total Votes: &f" + total,
                    "&7Monthly Votes: &f" + monthly,
                    "&7Current Rank: &e" + rankStr,
                    "&7Monthly Wins: &6" + wins));

            inv.setItem(22, createGuiItem(Material.ARROW, "&cBack", "&7Return to main menu"));

        } else if (type == GuiType.LEADERBOARD) {
            List<Map.Entry<UUID, Integer>> topVoters = plugin.getVoteDataHandler().getTopVoters();
            
            // Display Top 10 in a clean podium layout
            int[] slots = {13, 11, 15, 28, 29, 30, 31, 32, 33, 34};
            
            for (int i = 0; i < Math.min(topVoters.size(), slots.length); i++) {
                Map.Entry<UUID, Integer> entry = topVoters.get(i);
                OfflinePlayer p = Bukkit.getOfflinePlayer(entry.getKey());
                String name = p.getName() != null ? p.getName() : "Unknown";
                int rank = i + 1;
                String rankColor = (rank == 1) ? "&6&l" : ((rank == 2) ? "&f&l" : ((rank == 3) ? "&c&l" : "&e&l"));
                
                inv.setItem(slots[i], createHeadItem(p, rankColor + "#" + rank + " " + name, 
                        "&7Monthly Votes: &f" + entry.getValue(),
                        "&7Rank: " + rankColor + "#" + rank));
            }

            for (int i = topVoters.size(); i < slots.length; i++) {
                int rank = i + 1;
                inv.setItem(slots[i], createGuiItem(Material.GRAY_DYE, "&8#" + rank + " Empty", "&7No votes recorded yet."));
            }

            inv.setItem(49, createGuiItem(Material.ARROW, "&cBack", "&7Return to main menu"));
        } else if (type == GuiType.CLAIM) {
            int rank = plugin.getVoteDataHandler().getUnclaimedRewardRank(viewer.getUniqueId());
            
            if (rank != -1) {
                inv.setItem(13, createGuiItem(Material.EMERALD, "&a&lClaim Reward", 
                        "&7You were the &e#" + rank + " Top Voter", 
                        "&7last month!", 
                        "", 
                        "&eClick to claim your reward!"));
            } else {
                inv.setItem(13, createGuiItem(Material.BARRIER, "&c&lNo Rewards", 
                        "&7You do not have any", 
                        "&7unclaimed rewards."));
            }
        }
    }

    private ItemStack createGuiItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.colorize(name));
            List<String> loreList = new ArrayList<>();
            for (String line : lore) {
                loreList.add(ColorUtil.colorize(line));
            }
            meta.setLore(loreList);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createHeadItem(OfflinePlayer player, String name, String... lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD, 1);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(player);
            meta.setDisplayName(ColorUtil.colorize(name));
            List<String> loreList = new ArrayList<>();
            for (String line : lore) {
                loreList.add(ColorUtil.colorize(line));
            }
            meta.setLore(loreList);
            item.setItemMeta(meta);
        }
        return item;
    }

    public void open() {
        viewer.openInventory(inv);
    }
    
    public GuiType getType() {
        return type;
    }
}
