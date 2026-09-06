package org.mapplestudio.votify.data;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.mapplestudio.votify.Votify;
import org.mapplestudio.votify.util.ColorUtil;
import org.mapplestudio.votify.util.DiscordWebhook;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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

    // Async save queue / debouncing state
    private boolean isSaving = false;
    private boolean hasPendingSave = false;

    public VoteDataHandler(Votify plugin) {
        this.plugin = plugin;
        setup();
        cleanLegacyRootKeys();
        checkMonthlyReset();
        checkWeeklyReset();
    }

    public void setup() {
        voteDataFile = new File(plugin.getDataFolder(), "votedata.yml");
        if (!voteDataFile.exists()) {
            plugin.saveResource("votedata.yml", false);
        }
        synchronized (lock) {
            voteDataConfig = YamlConfiguration.loadConfiguration(voteDataFile);
        }
    }

    public FileConfiguration getVoteData() {
        synchronized (lock) {
            return voteDataConfig;
        }
    }

    public void cleanLegacyRootKeys() {
        synchronized (lock) {
            boolean modified = false;
            for (String key : new HashSet<>(voteDataConfig.getKeys(false))) {
                if (key.equals("players") || key.equals("queue") || key.equals("history") 
                        || key.equals("unclaimed_rewards") || key.equals("voteparty")) {
                    continue;
                }
                try {
                    UUID.fromString(key);
                    voteDataConfig.set(key, null);
                    modified = true;
                } catch (IllegalArgumentException ignored) {}
            }
            if (modified) {
                saveVoteDataSync();
            }
        }
    }

    public void saveVoteData() {
        synchronized (lock) {
            if (isSaving) {
                hasPendingSave = true;
                return;
            }
            isSaving = true;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::performSave);
    }

    private void performSave() {
        synchronized (lock) {
            try {
                File tempFile = new File(voteDataFile.getParentFile(), voteDataFile.getName() + ".tmp");
                voteDataConfig.save(tempFile);
                try {
                    Files.move(tempFile.toPath(), voteDataFile.toPath(),
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (Exception atomicEx) {
                    Files.move(tempFile.toPath(), voteDataFile.toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                plugin.getLogger().severe("Could not save votedata.yml!");
                e.printStackTrace();
            } finally {
                isSaving = false;
                if (hasPendingSave) {
                    hasPendingSave = false;
                    saveVoteData();
                }
            }
        }
    }

    public void saveVoteDataSync() {
        synchronized (lock) {
            try {
                voteDataConfig.save(voteDataFile);
            } catch (IOException e) {
                plugin.getLogger().severe("Could not save votedata.yml synchronously!");
                e.printStackTrace();
            }
        }
    }

    public void reloadVoteData() {
        synchronized (lock) {
            voteDataConfig = YamlConfiguration.loadConfiguration(voteDataFile);
            lastCacheUpdate = 0;
        }
    }

    public boolean isSqlite() {
        return plugin.getDatabaseManager() != null;
    }

    public void addVote(UUID playerUUID, String serviceName) {
        if (isSqlite()) {
            synchronized (lock) {
                checkMonthlyReset();
                checkWeeklyReset();
                OfflinePlayer op = Bukkit.getOfflinePlayer(playerUUID);
                String pName = op.getName() != null ? op.getName() : "Unknown";
                plugin.getDatabaseManager().addVote(playerUUID, pName, serviceName);

                // Vote Party Logic
                if (plugin.getVoteRewardsConfig().getBoolean("voteparty.enabled", false)) {
                    int currentPartyVotes = plugin.getDatabaseManager().getMetaInt("voteparty_current", 0) + 1;
                    int requiredVotes = plugin.getVoteRewardsConfig().getInt("voteparty.votes-required", 50);

                    if (currentPartyVotes >= requiredVotes) {
                        plugin.getDatabaseManager().setMetaInt("voteparty_current", 0);
                        Bukkit.getScheduler().runTask(plugin, this::triggerVoteParty);
                    } else {
                        plugin.getDatabaseManager().setMetaInt("voteparty_current", currentPartyVotes);
                        String progressMsg = plugin.getConfig().getString("messages.voteparty.progress");
                        if (progressMsg != null && !progressMsg.isEmpty()) {
                            String prefix = plugin.getConfig().getString("messages.prefix", "&8[&bVotify&8] &r");
                            progressMsg = progressMsg.replace("%prefix%", prefix)
                                                     .replace("%current_votes%", String.valueOf(currentPartyVotes))
                                                     .replace("%required_votes%", String.valueOf(requiredVotes));
                            Bukkit.broadcastMessage(ColorUtil.colorize(progressMsg));
                        }
                        if (plugin.getBossBar() != null) {
                            plugin.getBossBar().update(currentPartyVotes, requiredVotes);
                        }
                    }
                }
                lastCacheUpdate = 0;
            }
            return;
        }

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
                } else {
                    // Send progress message
                    String progressMsg = plugin.getConfig().getString("messages.voteparty.progress");
                    if (progressMsg != null && !progressMsg.isEmpty()) {
                        String prefix = plugin.getConfig().getString("messages.prefix", "&8[&bVotify&8] &r");
                        progressMsg = progressMsg.replace("%prefix%", prefix)
                                                 .replace("%current_votes%", String.valueOf(currentPartyVotes))
                                                 .replace("%required_votes%", String.valueOf(requiredVotes));
                        Bukkit.broadcastMessage(ColorUtil.colorize(progressMsg));
                    }
                    if (plugin.getBossBar() != null) {
                        plugin.getBossBar().update(currentPartyVotes, requiredVotes);
                    }
                }
            }
            
            lastCacheUpdate = 0;
        }
        saveVoteData();
    }

    public void triggerVoteParty() {
        List<String> messages = plugin.getConfig().getStringList("messages.voteparty.reached");
        for (String msg : messages) {
            Bukkit.broadcastMessage(ColorUtil.colorize(msg));
        }

        List<String> rewards = plugin.getVoteRewardsConfig().getStringList("voteparty.rewards");
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (String reward : rewards) {
                String finalReward = reward.replace("%player%", player.getName());
                plugin.getVoteListener().processRewardString(player, finalReward);
            }

            // Visual Celebration Effects
            try {
                Location loc = player.getLocation();
                Firework fw = player.getWorld().spawn(loc, Firework.class);
                FireworkMeta fwm = fw.getFireworkMeta();
                fwm.setPower(1);
                fwm.addEffect(FireworkEffect.builder()
                        .with(FireworkEffect.Type.BALL_LARGE)
                        .withColor(Color.FUCHSIA, Color.AQUA, Color.YELLOW)
                        .withFade(Color.WHITE)
                        .withFlicker()
                        .withTrail()
                        .build());
                fw.setFireworkMeta(fwm);

                player.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                player.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                player.sendTitle(
                        ColorUtil.colorize("&d&lVOTE PARTY!"),
                        ColorUtil.colorize("&fThe server vote goal has been reached!"),
                        10, 70, 20
                );
            } catch (Exception ignored) {}
        }

        if (plugin.getBossBar() != null) {
            plugin.getBossBar().update(0, getVotePartyRequired());
        }
    }

    public int getVotePartyCurrent() {
        if (isSqlite()) {
            return plugin.getDatabaseManager().getMetaInt("voteparty_current", 0);
        } else {
            return getVoteData().getInt("voteparty.current", 0);
        }
    }

    public int getVotePartyRequired() {
        return plugin.getVoteRewardsConfig().getInt("voteparty.votes-required", 50);
    }

    public void addVotePartyVotes(int amount) {
        int required = getVotePartyRequired();
        int current = getVotePartyCurrent() + amount;
        if (current >= required) {
            resetVoteParty();
            Bukkit.getScheduler().runTask(plugin, this::triggerVoteParty);
        } else {
            if (isSqlite()) {
                plugin.getDatabaseManager().setMetaInt("voteparty_current", current);
            } else {
                synchronized (lock) {
                    getVoteData().set("voteparty.current", current);
                }
                saveVoteData();
            }
            if (plugin.getBossBar() != null) {
                plugin.getBossBar().update(current, required);
            }
        }
    }

    public void resetVoteParty() {
        if (isSqlite()) {
            plugin.getDatabaseManager().setMetaInt("voteparty_current", 0);
        } else {
            synchronized (lock) {
                getVoteData().set("voteparty.current", 0);
            }
            saveVoteData();
        }
        if (plugin.getBossBar() != null) {
            plugin.getBossBar().update(0, getVotePartyRequired());
        }
    }

    public void setPlayerVotes(UUID uuid, String stat, int amount) {
        if (isSqlite()) {
            plugin.getDatabaseManager().setPlayerStat(uuid, stat, amount);
        } else {
            synchronized (lock) {
                String path = "players." + uuid.toString() + "." + stat.toLowerCase();
                getVoteData().set(path, amount);
            }
            saveVoteData();
        }
        lastCacheUpdate = 0;
    }

    public void resetPlayerData(UUID uuid) {
        if (isSqlite()) {
            plugin.getDatabaseManager().resetPlayerStats(uuid);
        } else {
            synchronized (lock) {
                String path = "players." + uuid.toString();
                getVoteData().set(path + ".total", 0);
                getVoteData().set(path + ".monthly", 0);
                getVoteData().set(path + ".weekly", 0);
                getVoteData().set(path + ".streak", 0);
                getVoteData().set(path + ".claimed-milestones", new ArrayList<String>());
            }
            saveVoteData();
        }
        lastCacheUpdate = 0;
    }

    public boolean hasClaimedMilestone(UUID uuid, int milestone) {
        if (isSqlite()) {
            return plugin.getDatabaseManager().hasClaimedMilestone(uuid, milestone);
        } else {
            synchronized (lock) {
                List<String> list = getVoteData().getStringList("players." + uuid.toString() + ".claimed-milestones");
                return list.contains(String.valueOf(milestone));
            }
        }
    }

    public void addClaimedMilestone(UUID uuid, int milestone) {
        if (isSqlite()) {
            plugin.getDatabaseManager().addClaimedMilestone(uuid, milestone);
        } else {
            synchronized (lock) {
                String path = "players." + uuid.toString() + ".claimed-milestones";
                List<String> list = getVoteData().getStringList(path);
                if (!list.contains(String.valueOf(milestone))) {
                    list.add(String.valueOf(milestone));
                    getVoteData().set(path, list);
                }
            }
            saveVoteData();
        }
    }

    public void addPendingReward(UUID playerUUID, String rewardString) {
        addPendingReward(playerUUID, rewardString, true);
    }

    public void addPendingReward(UUID playerUUID, String rewardString, boolean saveImmediately) {
        if (isSqlite()) {
            plugin.getDatabaseManager().addPendingReward(playerUUID, rewardString);
            return;
        }

        synchronized (lock) {
            List<String> pending = getVoteData().getStringList("queue." + playerUUID.toString());
            pending.add(rewardString);
            getVoteData().set("queue." + playerUUID.toString(), pending);
        }
        if (saveImmediately) {
            saveVoteData();
        }
    }

    public void addPendingRewards(UUID playerUUID, List<String> rewardStrings) {
        if (isSqlite()) {
            plugin.getDatabaseManager().addPendingRewards(playerUUID, rewardStrings);
            return;
        }

        synchronized (lock) {
            List<String> pending = getVoteData().getStringList("queue." + playerUUID.toString());
            pending.addAll(rewardStrings);
            getVoteData().set("queue." + playerUUID.toString(), pending);
        }
        saveVoteData();
    }

    public List<String> getPendingRewards(UUID playerUUID) {
        if (isSqlite()) {
            return plugin.getDatabaseManager().getPendingRewards(playerUUID);
        }

        synchronized (lock) {
            return getVoteData().getStringList("queue." + playerUUID.toString());
        }
    }

    public void clearPendingRewards(UUID playerUUID) {
        if (isSqlite()) {
            plugin.getDatabaseManager().clearPendingRewards(playerUUID);
            return;
        }

        synchronized (lock) {
            getVoteData().set("queue." + playerUUID.toString(), null);
        }
        saveVoteData();
    }

    public void checkMonthlyReset() {
        synchronized (lock) {
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
    }
    
    public void checkWeeklyReset() {
        synchronized (lock) {
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
    }

    private void processMonthlyReset(int previousMonth) {
        plugin.getLogger().info("Processing monthly reset for month: " + previousMonth);

        // 1. Get Top Voters
        List<Map.Entry<UUID, Integer>> topVoters = getTopVoters();
        
        // 2. Store Unclaimed Rewards (Manual Claim System)
        String monthKey = LocalDate.now().minusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"));
        storeUnclaimedRewards(topVoters, monthKey);

        if (isSqlite()) {
            plugin.getDatabaseManager().processMonthlyReset(monthKey, topVoters);
            if (plugin.getConfig().getBoolean("discord.enabled")) {
                sendDiscordWebhook(topVoters, monthKey);
            }
            lastCacheUpdate = 0;
            return;
        }

        // 3. Save History & Update Streaks (YAML fallback)
        int totalMonthlyServerVotes = 0;
        ConfigurationSection players = getVoteData().getConfigurationSection("players");
        if (players != null) {
            for (String uuid : players.getKeys(false)) {
                totalMonthlyServerVotes += players.getInt(uuid + ".monthly", 0);
            }
        }
        getVoteData().set("history." + monthKey + ".total_server_votes", totalMonthlyServerVotes);

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
        if (players != null) {
            for (String uuid : players.getKeys(false)) {
                int monthly = players.getInt(uuid + ".monthly", 0);
                int currentStreak = players.getInt(uuid + ".streak", 0);
                
                if (monthly > 0) {
                    currentStreak++;
                    getVoteData().set("players." + uuid + ".streak", currentStreak);
                    
                    int bestStreak = players.getInt(uuid + ".best-streak", 0);
                    if (currentStreak > bestStreak) {
                        getVoteData().set("players." + uuid + ".best-streak", currentStreak);
                    }
                } else {
                    getVoteData().set("players." + uuid + ".streak", 0);
                }
                
                // Reset Monthly properly under players.<uuid>.monthly
                getVoteData().set("players." + uuid + ".monthly", 0);
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
        if (isSqlite()) {
            plugin.getDatabaseManager().processWeeklyReset();
            return;
        }

        ConfigurationSection players = getVoteData().getConfigurationSection("players");
        if (players != null) {
            for (String uuid : players.getKeys(false)) {
                getVoteData().set("players." + uuid + ".weekly", 0);
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
                if (isSqlite()) {
                    plugin.getDatabaseManager().storeUnclaimedReward(monthKey, uuid, rank);
                } else {
                    getVoteData().set("unclaimed_rewards." + monthKey + "." + uuid.toString() + ".rank", rank);
                    getVoteData().set("unclaimed_rewards." + monthKey + "." + uuid.toString() + ".timestamp", System.currentTimeMillis());
                }
            }
        }
    }

    public int getUnclaimedRewardRank(UUID uuid) {
        if (isSqlite()) {
            return plugin.getDatabaseManager().getUnclaimedRewardRank(uuid);
        }

        synchronized (lock) {
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
    }

    public boolean claimReward(UUID uuid) {
        if (isSqlite()) {
            int rank = plugin.getDatabaseManager().getUnclaimedRewardRank(uuid);
            if (rank == -1) return false;

            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
            if (offlinePlayer.isOnline()) {
                Player player = offlinePlayer.getPlayer();
                List<String> rewards = getRewardsForRank(rank);
                int requiredSlots = calculateRequiredSlots(rewards);
                
                if (getEmptySlots(player) < requiredSlots) {
                    return false; // Inventory full
                }
            }

            distributeRewardForRank(uuid, rank);
            plugin.getDatabaseManager().removeUnclaimedReward(uuid);
            return true;
        }

        synchronized (lock) {
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

        List<String> formattedRewards = new ArrayList<>();
        for (String reward : rewards) {
            formattedRewards.add(reward.replace("%player%", playerName));
        }
        addPendingRewards(uuid, formattedRewards);
        
        if (player.isOnline()) {
            plugin.getVoteListener().processPendingRewards(player.getPlayer());
        }
    }

    // Used for Admin Force Give
    public int distributeTopVoterRewards(List<Map.Entry<UUID, Integer>> topVoters) {
        synchronized (lock) {
            String monthKey = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM")); // Use current month for forced rewards
            storeUnclaimedRewards(topVoters, monthKey);
            if (!isSqlite()) {
                saveVoteData();
            }
            return Math.min(topVoters.size(), 10);
        }
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
        if (isSqlite()) {
            return plugin.getDatabaseManager().getTopVoters(10);
        }

        synchronized (lock) {
            if (System.currentTimeMillis() - lastCacheUpdate < 60000 && !cachedTopVoters.isEmpty()) {
                return new ArrayList<>(cachedTopVoters);
            }

            Map<UUID, Integer> votes = new HashMap<>();
            ConfigurationSection players = getVoteData().getConfigurationSection("players");
            if (players != null) {
                for (String uuidStr : players.getKeys(false)) {
                    int monthly = players.getInt(uuidStr + ".monthly", 0);
                    if (monthly > 0) {
                        votes.put(UUID.fromString(uuidStr), monthly);
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
    }
    
    public List<Map.Entry<UUID, Integer>> getWeeklyTopVoters() {
        if (isSqlite()) {
            return plugin.getDatabaseManager().getWeeklyTopVoters(10);
        }

        synchronized (lock) {
            Map<UUID, Integer> votes = new HashMap<>();
            ConfigurationSection players = getVoteData().getConfigurationSection("players");
            if (players != null) {
                for (String uuidStr : players.getKeys(false)) {
                    int weekly = players.getInt(uuidStr + ".weekly", 0);
                    if (weekly > 0) {
                        votes.put(UUID.fromString(uuidStr), weekly);
                    }
                }
            }
            return votes.entrySet().stream()
                    .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                    .collect(Collectors.toList());
        }
    }

    public List<Map.Entry<UUID, Integer>> getAllTimeTopVoters() {
        if (isSqlite()) {
            return plugin.getDatabaseManager().getAllTimeTopVoters(10);
        }

        synchronized (lock) {
            Map<UUID, Integer> votes = new HashMap<>();
            ConfigurationSection players = getVoteData().getConfigurationSection("players");
            if (players != null) {
                for (String uuidStr : players.getKeys(false)) {
                    int total = players.getInt(uuidStr + ".total", 0);
                    if (total > 0) {
                        votes.put(UUID.fromString(uuidStr), total);
                    }
                }
            }

            return votes.entrySet().stream()
                    .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                    .collect(Collectors.toList());
        }
    }
    
    public int getTotalServerVotes() {
        if (isSqlite()) {
            return plugin.getDatabaseManager().getTotalServerVotes();
        }

        synchronized (lock) {
            int total = 0;
            ConfigurationSection players = getVoteData().getConfigurationSection("players");
            if (players != null) {
                for (String uuidStr : players.getKeys(false)) {
                    total += players.getInt(uuidStr + ".total", 0);
                }
            }
            return total;
        }
    }

    public int getLastMonthTotal() {
        String lastMonthKey = LocalDate.now().minusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"));
        if (isSqlite()) {
            return plugin.getDatabaseManager().getLastMonthTotal(lastMonthKey);
        }

        synchronized (lock) {
            if (getVoteData().contains("history." + lastMonthKey + ".total_server_votes")) {
                return getVoteData().getInt("history." + lastMonthKey + ".total_server_votes", 0);
            }
            ConfigurationSection monthSection = getVoteData().getConfigurationSection("history." + lastMonthKey);
            if (monthSection != null) {
                int total = 0;
                for (String rankKey : monthSection.getKeys(false)) {
                    if (!rankKey.equals("total_server_votes")) {
                        total += monthSection.getInt(rankKey + ".votes", 0);
                    }
                }
                return total;
            }
            return 0;
        }
    }

    public int getPlayerStat(UUID uuid, String statName) {
        if (isSqlite()) {
            Map<String, Object> stats = plugin.getDatabaseManager().getPlayerStats(uuid);
            Object val = stats.get(statName);
            if (val instanceof Number) {
                return ((Number) val).intValue();
            }
            return 0;
        } else {
            return getVoteData().getInt("players." + uuid.toString() + "." + statName, 0);
        }
    }
}
