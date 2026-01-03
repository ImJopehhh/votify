package org.mapplestudio.votify.data;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
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
            String path = "players." + playerUUID.toString();
            int totalVotes = getVoteData().getInt(path + ".total", 0) + 1;
            int monthlyVotes = getVoteData().getInt(path + ".monthly", 0) + 1;
            int weeklyVotes = getVoteData().getInt(path + ".weekly", 0) + 1;

            getVoteData().set(path + ".total", totalVotes);
            getVoteData().set(path + ".monthly", monthlyVotes);
            getVoteData().set(path + ".weekly", weeklyVotes);
            getVoteData().set(path + ".last-vote-service", serviceName);
            getVoteData().set(path + ".last-vote-time", System.currentTimeMillis());
            
            lastCacheUpdate = 0;
        }
        saveVoteData();
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

    private void processMonthlyReset(int previousMonth) {
        plugin.getLogger().info("Processing monthly reset for month: " + previousMonth);

        // 1. Get Top Voters
        List<Map.Entry<UUID, Integer>> topVoters = getTopVoters();
        
        // 2. Distribute Rewards Automatically
        distributeTopVoterRewards(topVoters);

        // 3. Save History
        String monthKey = LocalDate.now().minusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"));
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

        // 6. Reset Monthly Votes
        ConfigurationSection players = getVoteData().getConfigurationSection("players");
        if (players != null) {
            for (String uuid : players.getKeys(false)) {
                getVoteData().set("players." + uuid + ".monthly", 0);
            }
        }
        
        lastCacheUpdate = 0;
        saveVoteData();
    }

    public int distributeTopVoterRewards(List<Map.Entry<UUID, Integer>> topVoters) {
        ConfigurationSection topRewards = plugin.getVoteRewardsConfig().getConfigurationSection("topvoterrewards");
        if (topRewards == null) return 0;

        int count = 0;
        for (int i = 0; i < Math.min(topVoters.size(), 10); i++) {
            int rank = i + 1;
            Map.Entry<UUID, Integer> entry = topVoters.get(i);
            OfflinePlayer player = Bukkit.getOfflinePlayer(entry.getKey());
            String playerName = player.getName() != null ? player.getName() : "Unknown";

            List<String> rewards = new ArrayList<>();
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

            if (!rewards.isEmpty()) {
                count++;
                for (String reward : rewards) {
                    reward = reward.replace("%player%", playerName);
                    addPendingReward(player.getUniqueId(), reward);
                }
                
                if (player.isOnline()) {
                    plugin.getVoteListener().processPendingRewards(player.getPlayer());
                }
            }
        }
        return count;
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
}
