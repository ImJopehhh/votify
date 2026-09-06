package org.mapplestudio.votify.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.mapplestudio.votify.Votify;

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
        MAIN, STATS, LEADERBOARD, CLAIM
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
            case STATS: return ChatColor.DARK_AQUA + "Your Statistics";
            case LEADERBOARD: return ChatColor.GOLD + "Top Voters (Monthly)";
            case CLAIM: return ChatColor.GREEN + "Claim Reward";
            default: return ChatColor.BLUE + "Votify Menu";
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
            // Stats Button
            inv.setItem(11, createHeadItem(viewer, "&b&lYour Stats", 
                    "&7Click to view your", "&7voting statistics."));

            // Leaderboard Button
            inv.setItem(15, createGuiItem(Material.GOLD_INGOT, "&6&lLeaderboard", 
                    "&7Click to view the", "&7monthly top voters."));

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
            // Rank 1 at slot 13, Rank 2 at slot 11, Rank 3 at slot 15
            // Ranks 4-10 across row 4 (slots 28 to 34)
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
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        List<String> loreList = new ArrayList<>();
        for (String line : lore) {
            loreList.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        meta.setLore(loreList);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createHeadItem(OfflinePlayer player, String name, String... lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD, 1);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(player);
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        List<String> loreList = new ArrayList<>();
        for (String line : lore) {
            loreList.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        meta.setLore(loreList);
        item.setItemMeta(meta);
        return item;
    }

    public void open() {
        viewer.openInventory(inv);
    }
    
    public GuiType getType() {
        return type;
    }
}
