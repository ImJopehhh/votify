package org.mapplestudio.votify.data;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.mapplestudio.votify.Votify;
import org.mapplestudio.votify.util.DiscordWebhook;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class VoteDataHandler {

    private final Votify plugin;
    private FileConfiguration voteDataConfig;
    private File voteDataFile;
    private final Object lock = new Object();
    
    // Cache for realtime top voters
    private List<Map.Entry<UUID, Integer>> cachedTopVoters = new ArrayList<>();
    private long lastCacheUpdate = 0;

    public VoteDataHandler(Votify plugin) {
        this.plugin = plugin;
        setup();
        checkMonthlyReset();
        checkWeeklyReset();
    }

    public void setup() {
        voteDataFile = new File(plugin.getDataFolder(), "votedata.yml");
        if (!voteDataFile.exists()) {
            plugin.saveResource("votedata.yml", false);
        }
        voteDataConfig = YamlConfiguration.loadConfiguration(voteDataFile);
    }

    public FileConfiguration getVoteData() {
        return voteDataConfig;
    }

    public void saveVoteData() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            synchronized (lock) {
                try {
                    voteDataConfig.save(voteDataFile);
                } catch (IOException e) {
                    plugin.getLogger().severe("Could not save votedata.yml!");
                    e.printStackTrace();
                }
            }
        });
    }

    public void reloadVoteData() {
        synchronized (lock) {
            voteDataConfig = YamlConfiguration.loadConfiguration(voteDataFile);
        }
    }

    public void addVote(UUID playerUUID, String serviceName) {
        synchronized (lock) {
            checkMonthlyReset();
            checkWeeklyReset();
            String path = "players." + playerUUID.toString();
            int totalVotes = getVoteData().getInt(path + ".total", 0) + 1;
            int monthlyVotes = getVoteData().getInt(path + ".monthly", 0) + 1;
            int weeklyVotes = getVoteData().getInt(path + ".weekly", 0) + 1;

            getVoteData().set(path + ".total", totalVotes);
            getVoteData().set(path + ".monthly", monthlyVotes);
            getVoteData().set(path + ".weekly", weeklyVotes);
            getVoteData().set(path + ".last-vote-service", serviceName);
            getVoteData().set(path + ".last-vote-time", System.currentTimeMillis());
            
            // Update Best Stats
            int bestWeekly = getVoteData().getInt(path + ".best-weekly", 0);
            if (weeklyVotes > bestWeekly) {
                getVoteData().set(path + ".best-weekly", weeklyVotes);
            }
            
            int bestMonthly = getVoteData().getInt(path + ".best-monthly", 0);
            if (monthlyVotes > bestMonthly) {
                getVoteData().set(path + ".best-monthly", monthlyVotes);
            }

            // Vote Party Logic
            if (plugin.getVoteRewardsConfig().getBoolean("voteparty.enabled", false)) {
                int currentPartyVotes = getVoteData().getInt("voteparty.current", 0) + 1;
                int requiredVotes = plugin.getVoteRewardsConfig().getInt("voteparty.votes-required", 50);
                
                getVoteData().set("voteparty.current", currentPartyVotes);
                
                // Track contribution
                int contribution = getVoteData().getInt(path + ".voteparty-contribution", 0) + 1;
                getVoteData().set(path + ".voteparty-contribution", contribution);

                if (currentPartyVotes >= requiredVotes) {
                    // Trigger Vote Party
                    Bukkit.getScheduler().runTask(plugin, this::triggerVoteParty);
                    getVoteData().set("voteparty.current", 0);
                    // Reset contributions? Usually kept until next party or forever. Let's keep for now.
                } else {
                    // Send progress message
                    String progressMsg = plugin.getConfig().getString("messages.voteparty.progress");
                    if (progressMsg != null && !progressMsg.isEmpty()) {
                        progressMsg = progressMsg.replace("%current_votes%", String.valueOf(currentPartyVotes))
                                                 .replace("%required_votes%", String.valueOf(requiredVotes));
                        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', progressMsg));
                    }
                }
            }
            
            lastCacheUpdate = 0;
        }
        saveVoteData();
    }

    private void triggerVoteParty() {
        List<String> messages = plugin.getConfig().getStringList("messages.voteparty.reached");
        for (String msg : messages) {
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', msg));
        }

        List<String> rewards = plugin.getVoteRewardsConfig().getStringList("voteparty.rewards");
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (String reward : rewards) {
                String finalReward = reward.replace("%player%", player.getName());
                // Execute immediately for online players
                plugin.getVoteListener().processRewardString(player, finalReward);
            }
        }
    }

    public void addPendingReward(UUID playerUUID, String rewardString) {
        synchronized (lock) {
            List<String> pending = getVoteData().getStringList("queue." + playerUUID.toString());
            pending.add(rewardString);
            getVoteData().set("queue." + playerUUID.toString(), pending);
        }
        saveVoteData();
    }

    public List<String> getPendingRewards(UUID playerUUID) {
        synchronized (lock) {
            return getVoteData().getStringList("queue." + playerUUID.toString());
        }
    }

    public void clearPendingRewards(UUID playerUUID) {
        synchronized (lock) {
            getVoteData().set("queue." + playerUUID.toString(), null);
        }
        saveVoteData();
    }

    private void checkMonthlyReset() {
        int currentMonth = LocalDate.now().getMonthValue();
        int lastMonth = plugin.getConfig().getInt("data.last-month", -1);

        if (lastMonth != -1 && lastMonth != currentMonth) {
            processMonthlyReset(lastMonth);
        }

        if (lastMonth != currentMonth) {
            plugin.getConfig().set("data.last-month", currentMonth);
            plugin.saveConfig();
        }
    }
    
    private void checkWeeklyReset() {
        // Simple week check using Calendar week or just day of year / 7
        // Better: Use ISO week number
        int currentWeek = Calendar.getInstance().get(Calendar.WEEK_OF_YEAR);
        int lastWeek = plugin.getConfig().getInt("data.last-week", -1);
        
        if (lastWeek != -1 && lastWeek != currentWeek) {
            processWeeklyReset();
        }
        
        if (lastWeek != currentWeek) {
            plugin.getConfig().set("data.last-week", currentWeek);
            plugin.saveConfig();
        }
    }

    private void processMonthlyReset(int previousMonth) {
        plugin.getLogger().info("Processing monthly reset for month: " + previousMonth);

        // 1. Get Top Voters
        List<Map.Entry<UUID, Integer>> topVoters = getTopVoters();
        
        // 2. Store Unclaimed Rewards (Manual Claim System)
        String monthKey = LocalDate.now().minusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"));
        storeUnclaimedRewards(topVoters, monthKey);

        // 3. Save History & Update Streaks
        for (int i = 0; i < Math.min(topVoters.size(), 10); i++) {
            Map.Entry<UUID, Integer> entry = topVoters.get(i);
            String path = "history." + monthKey + "." + (i + 1);
            getVoteData().set(path + ".uuid", entry.getKey().toString());
            getVoteData().set(path + ".votes", entry.getValue());
            
            if (i == 0) { // Top 1
                String playerPath = "players." + entry.getKey().toString();
                int wins = getVoteData().getInt(playerPath + ".wins", 0) + 1;
                getVoteData().set(playerPath + ".wins", wins);
            }
        }
        
        // Update Streaks for all players who voted this month
        ConfigurationSection players = getVoteData().getConfigurationSection("players");
        if (players != null) {
            for (String uuid : players.getKeys(false)) {
                int monthly = players.getInt(uuid + ".monthly", 0);
                int currentStreak = players.getInt(uuid + ".streak", 0);
                
                if (monthly > 0) {
                    currentStreak++;
                    getVoteData().set(uuid + ".streak", currentStreak);
                    
                    int bestStreak = players.getInt(uuid + ".best-streak", 0);
                    if (currentStreak > bestStreak) {
                        getVoteData().set(uuid + ".best-streak", currentStreak);
                    }
                } else {
                    getVoteData().set(uuid + ".streak", 0);
                }
                
                // Reset Monthly
                getVoteData().set(uuid + ".monthly", 0);
            }
        }

        // 4. Clean old history
        ConfigurationSection historySection = getVoteData().getConfigurationSection("history");
        if (historySection != null) {
            List<String> keys = new ArrayList<>(historySection.getKeys(false));
            if (keys.size() > 12) {
                Collections.sort(keys);
                while (keys.size() > 12) {
                    String oldKey = keys.remove(0);
                    getVoteData().set("history." + oldKey, null);
                }
            }
        }

        // 5. Send Discord Webhook
        if (plugin.getConfig().getBoolean("discord.enabled")) {
            sendDiscordWebhook(topVoters, monthKey);
        }
        
        lastCacheUpdate = 0;
        saveVoteData();
    }
    
    private void processWeeklyReset() {
        plugin.getLogger().info("Processing weekly reset.");
        ConfigurationSection players = getVoteData().getConfigurationSection("players");
        if (players != null) {
            for (String uuid : players.getKeys(false)) {
                getVoteData().set(uuid + ".weekly", 0);
            }
        }
        saveVoteData();
    }

    private void storeUnclaimedRewards(List<Map.Entry<UUID, Integer>> topVoters, String monthKey) {
        ConfigurationSection topRewards = plugin.getVoteRewardsConfig().getConfigurationSection("topvoterrewards");
        if (topRewards == null) return;

        for (int i = 0; i < Math.min(topVoters.size(), 10); i++) {
            int rank = i + 1;
            UUID uuid = topVoters.get(i).getKey();
            
            // Check if this rank has rewards
            boolean hasReward = false;
            for (String key : topRewards.getKeys(false)) {
                String[] ranks = key.split(",");
                for (String r : ranks) {
                    try {
                        if (Integer.parseInt(r.trim()) == rank) {
                            hasReward = true;
                            break;
                        }
                    } catch (NumberFormatException ignored) {}
                }
                if (hasReward) break;
            }

            if (hasReward) {
                getVoteData().set("unclaimed_rewards." + monthKey + "." + uuid.toString() + ".rank", rank);
                getVoteData().set("unclaimed_rewards." + monthKey + "." + uuid.toString() + ".timestamp", System.currentTimeMillis());
            }
        }
    }

    public int getUnclaimedRewardRank(UUID uuid) {
        ConfigurationSection unclaimed = getVoteData().getConfigurationSection("unclaimed_rewards");
        if (unclaimed == null) return -1;

        for (String monthKey : unclaimed.getKeys(false)) {
            if (unclaimed.contains(monthKey + "." + uuid.toString())) {
                long timestamp = unclaimed.getLong(monthKey + "." + uuid.toString() + ".timestamp");
                // Check 28 days expiration (28 * 24 * 60 * 60 * 1000 = 2419200000 ms)
                if (System.currentTimeMillis() - timestamp > 2419200000L) {
                    // Expired
                    getVoteData().set("unclaimed_rewards." + monthKey + "." + uuid.toString(), null);
                    saveVoteData();
                    continue;
                }
                return unclaimed.getInt(monthKey + "." + uuid.toString() + ".rank");
            }
        }
        return -1;
    }

    public boolean claimReward(UUID uuid) {
        ConfigurationSection unclaimed = getVoteData().getConfigurationSection("unclaimed_rewards");
        if (unclaimed == null) return false;

        for (String monthKey : unclaimed.getKeys(false)) {
            if (unclaimed.contains(monthKey + "." + uuid.toString())) {
                int rank = unclaimed.getInt(monthKey + "." + uuid.toString() + ".rank");
                
                // Check Inventory Space if player is online
                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
                if (offlinePlayer.isOnline()) {
                    Player player = offlinePlayer.getPlayer();
                    List<String> rewards = getRewardsForRank(rank);
                    int requiredSlots = calculateRequiredSlots(rewards);
                    
                    if (getEmptySlots(player) < requiredSlots) {
                        return false; // Inventory full
                    }
                }

                // Distribute rewards
                distributeRewardForRank(uuid, rank);
                
                // Remove from unclaimed
                getVoteData().set("unclaimed_rewards." + monthKey + "." + uuid.toString(), null);
                saveVoteData();
                return true;
            }
        }
        return false;
    }

    private List<String> getRewardsForRank(int rank) {
        ConfigurationSection topRewards = plugin.getVoteRewardsConfig().getConfigurationSection("topvoterrewards");
        List<String> rewards = new ArrayList<>();
        if (topRewards == null) return rewards;

        for (String key : topRewards.getKeys(false)) {
            String[] ranks = key.split(",");
            for (String r : ranks) {
                try {
                    if (Integer.parseInt(r.trim()) == rank) {
                        rewards.addAll(topRewards.getStringList(key));
                        break;
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        return rewards;
    }

    private int calculateRequiredSlots(List<String> rewards) {
        int slots = 0;
        for (String reward : rewards) {
            if (reward.toLowerCase().startsWith("item:")) {
                slots++;
            }
        }
        return slots;
    }

    private int getEmptySlots(Player player) {
        int empty = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() == Material.AIR) {
                empty++;
            }
        }
        return empty;
    }

    private void distributeRewardForRank(UUID uuid, int rank) {
        List<String> rewards = getRewardsForRank(rank);
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        String playerName = player.getName() != null ? player.getName() : "Unknown";

        for (String reward : rewards) {
            reward = reward.replace("%player%", playerName);
            addPendingReward(uuid, reward);
        }
        
        if (player.isOnline()) {
            plugin.getVoteListener().processPendingRewards(player.getPlayer());
        }
    }

    // Used for Admin Force Give
    public int distributeTopVoterRewards(List<Map.Entry<UUID, Integer>> topVoters) {
        String monthKey = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM")); // Use current month for forced rewards
        storeUnclaimedRewards(topVoters, monthKey);
        return Math.min(topVoters.size(), 10);
    }

    private void sendDiscordWebhook(List<Map.Entry<UUID, Integer>> topVoters, String monthName) {
        String url = plugin.getConfig().getString("discord.webhook-url");
        if (url == null || url.isEmpty()) return;

        DiscordWebhook webhook = new DiscordWebhook(url);
        String description = plugin.getConfig().getString("discord.top-voter-embed.description", "Top voters for %month%")
                .replace("%month%", monthName);
        
        StringBuilder sb = new StringBuilder();
        sb.append(description).append("\n\n");

        for (int i = 0; i < Math.min(topVoters.size(), 10); i++) {
            Map.Entry<UUID, Integer> entry = topVoters.get(i);
            OfflinePlayer p = Bukkit.getOfflinePlayer(entry.getKey());
            String name = p.getName() != null ? p.getName() : "Unknown";
            sb.append("**").append(i + 1).append(".** ").append(name).append(" - ").append(entry.getValue()).append(" votes\n");
        }

        DiscordWebhook.EmbedObject embed = new DiscordWebhook.EmbedObject()
                .setTitle(plugin.getConfig().getString("discord.top-voter-embed.title", "Monthly Top Voters"))
                .setDescription(sb.toString())
                .setColor(plugin.getConfig().getInt("discord.top-voter-embed.color", 16776960))
                .setFooter(plugin.getConfig().getString("discord.top-voter-embed.footer", "Votify"));

        webhook.addEmbed(embed);
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, webhook::execute);
    }

    public List<Map.Entry<UUID, Integer>> getTopVoters() {
        if (System.currentTimeMillis() - lastCacheUpdate < 60000 && !cachedTopVoters.isEmpty()) {
            return new ArrayList<>(cachedTopVoters);
        }

        Map<UUID, Integer> votes = new HashMap<>();
        synchronized (lock) {
            ConfigurationSection players = getVoteData().getConfigurationSection("players");
            if (players != null) {
                for (String uuidStr : players.getKeys(false)) {
                    int monthly = players.getInt(uuidStr + ".monthly", 0);
                    if (monthly > 0) {
                        votes.put(UUID.fromString(uuidStr), monthly);
                    }
                }
            }
        }

        List<Map.Entry<UUID, Integer>> sorted = votes.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .collect(Collectors.toList());
        
        cachedTopVoters = sorted;
        lastCacheUpdate = System.currentTimeMillis();
        
        return sorted;
    }
    
    public List<Map.Entry<UUID, Integer>> getWeeklyTopVoters() {
        Map<UUID, Integer> votes = new HashMap<>();
        synchronized (lock) {
            ConfigurationSection players = getVoteData().getConfigurationSection("players");
            if (players != null) {
                for (String uuidStr : players.getKeys(false)) {
                    int weekly = players.getInt(uuidStr + ".weekly", 0);
                    if (weekly > 0) {
                        votes.put(UUID.fromString(uuidStr), weekly);
                    }
                }
            }
        }
        return votes.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .collect(Collectors.toList());
    }

    public List<Map.Entry<UUID, Integer>> getAllTimeTopVoters() {
        Map<UUID, Integer> votes = new HashMap<>();
        synchronized (lock) {
            ConfigurationSection players = getVoteData().getConfigurationSection("players");
            if (players != null) {
                for (String uuidStr : players.getKeys(false)) {
                    int total = players.getInt(uuidStr + ".total", 0);
                    if (total > 0) {
                        votes.put(UUID.fromString(uuidStr), total);
                    }
                }
            }
        }

        return votes.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .collect(Collectors.toList());
    }
    
    public int getTotalServerVotes() {
        int total = 0;
        synchronized (lock) {
            ConfigurationSection players = getVoteData().getConfigurationSection("players");
            if (players != null) {
                for (String uuidStr : players.getKeys(false)) {
                    total += players.getInt(uuidStr + ".total", 0);
                }
            }
        }
        return total;
    }
}
