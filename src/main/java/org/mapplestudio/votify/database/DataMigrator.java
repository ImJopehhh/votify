package org.mapplestudio.votify.database;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;
import java.util.UUID;

public class DataMigrator {

    public static boolean migrateIfNecessary(JavaPlugin plugin, DatabaseManager dbManager) {
        if (!dbManager.isEmpty()) {
            return false;
        }

        File yamlFile = new File(plugin.getDataFolder(), "votedata.yml");
        if (!yamlFile.exists()) {
            return false;
        }

        FileConfiguration yaml = YamlConfiguration.loadConfiguration(yamlFile);
        ConfigurationSection playersSection = yaml.getConfigurationSection("players");
        if (playersSection == null || playersSection.getKeys(false).isEmpty()) {
            return false;
        }

        plugin.getLogger().info("Found legacy votedata.yml with existing player data. Starting automatic SQLite migration...");

        int migratedCount = 0;
        for (String uuidStr : playersSection.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                String path = "players." + uuidStr;
                int total = yaml.getInt(path + ".total", 0);
                int monthly = yaml.getInt(path + ".monthly", 0);
                int weekly = yaml.getInt(path + ".weekly", 0);
                int streak = yaml.getInt(path + ".streak", 0);
                int bestStreak = yaml.getInt(path + ".best-streak", 0);
                int bestMonthly = yaml.getInt(path + ".best-monthly", 0);
                int bestWeekly = yaml.getInt(path + ".best-weekly", 0);
                int wins = yaml.getInt(path + ".wins", 0);
                int contribution = yaml.getInt(path + ".voteparty-contribution", 0);
                String lastService = yaml.getString(path + ".last-vote-service", "Unknown");
                long lastTime = yaml.getLong(path + ".last-vote-time", 0);

                OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                String playerName = op.getName() != null ? op.getName() : "Unknown";

                dbManager.insertMigratedPlayer(uuid, playerName, total, monthly, weekly,
                        streak, bestStreak, bestMonthly, bestWeekly, wins, contribution, lastService, lastTime);
                migratedCount++;
            } catch (IllegalArgumentException ignored) {}
        }

        // Migrate pending rewards
        ConfigurationSection queueSection = yaml.getConfigurationSection("queue");
        if (queueSection != null) {
            for (String uuidStr : queueSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    List<String> rewards = queueSection.getStringList(uuidStr);
                    if (!rewards.isEmpty()) {
                        dbManager.addPendingRewards(uuid, rewards);
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }

        // Migrate unclaimed rewards
        ConfigurationSection unclaimedSection = yaml.getConfigurationSection("unclaimed_rewards");
        if (unclaimedSection != null) {
            for (String monthKey : unclaimedSection.getKeys(false)) {
                ConfigurationSection monthSec = unclaimedSection.getConfigurationSection(monthKey);
                if (monthSec != null) {
                    for (String uuidStr : monthSec.getKeys(false)) {
                        try {
                            UUID uuid = UUID.fromString(uuidStr);
                            int rank = monthSec.getInt(uuidStr + ".rank", 1);
                            dbManager.storeUnclaimedReward(monthKey, uuid, rank);
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
            }
        }

        // Migrate Vote Party count
        int partyCurrent = yaml.getInt("voteparty.current", 0);
        dbManager.setMetaInt("voteparty_current", partyCurrent);

        // Mark migration completed
        dbManager.setMeta("migrated_from_yaml", "true");

        plugin.getLogger().info("SQLite auto-migration completed successfully! Migrated " + migratedCount + " players.");
        return true;
    }
}
