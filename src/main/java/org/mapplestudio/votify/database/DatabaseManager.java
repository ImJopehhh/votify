package org.mapplestudio.votify.database;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.*;
import java.util.*;

public class DatabaseManager {

    private final JavaPlugin plugin;
    private final File dbFile;
    private Connection connection;
    private final Object lock = new Object();

    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dbFile = new File(plugin.getDataFolder(), "votify.db");
    }

    public void initialize() throws SQLException {
        synchronized (lock) {
            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException e) {
                plugin.getLogger().severe("SQLite JDBC Driver not found!");
                throw new SQLException("SQLite JDBC not available", e);
            }

            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }

            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());

            try (Statement stmt = connection.createStatement()) {
                // Enable WAL mode for better concurrency and performance
                stmt.execute("PRAGMA journal_mode = WAL;");
                stmt.execute("PRAGMA synchronous = NORMAL;");

                // 1. Players Table
                stmt.execute("CREATE TABLE IF NOT EXISTS votify_players (" +
                        "uuid VARCHAR(36) PRIMARY KEY, " +
                        "name VARCHAR(32), " +
                        "total_votes INT DEFAULT 0, " +
                        "monthly_votes INT DEFAULT 0, " +
                        "weekly_votes INT DEFAULT 0, " +
                        "streak INT DEFAULT 0, " +
                        "best_streak INT DEFAULT 0, " +
                        "best_monthly INT DEFAULT 0, " +
                        "best_weekly INT DEFAULT 0, " +
                        "wins INT DEFAULT 0, " +
                        "voteparty_contribution INT DEFAULT 0, " +
                        "last_vote_service VARCHAR(64), " +
                        "last_vote_time BIGINT DEFAULT 0" +
                        ");");

                stmt.execute("CREATE INDEX IF NOT EXISTS idx_monthly ON votify_players(monthly_votes DESC);");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_weekly ON votify_players(weekly_votes DESC);");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_total ON votify_players(total_votes DESC);");

                // 2. Queue Table
                stmt.execute("CREATE TABLE IF NOT EXISTS votify_queue (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "uuid VARCHAR(36) NOT NULL, " +
                        "reward_string TEXT NOT NULL" +
                        ");");

                // 3. Unclaimed Rewards Table
                stmt.execute("CREATE TABLE IF NOT EXISTS votify_unclaimed (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "month_key VARCHAR(10) NOT NULL, " +
                        "uuid VARCHAR(36) NOT NULL, " +
                        "rank INT NOT NULL, " +
                        "timestamp BIGINT NOT NULL" +
                        ");");

                // 4. History Table
                stmt.execute("CREATE TABLE IF NOT EXISTS votify_history (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "month_key VARCHAR(10) NOT NULL, " +
                        "rank INT NOT NULL, " +
                        "uuid VARCHAR(36) NOT NULL, " +
                        "votes INT NOT NULL" +
                        ");");

                // 5. Metadata Table (for VoteParty count, server totals, etc.)
                stmt.execute("CREATE TABLE IF NOT EXISTS votify_metadata (" +
                        "meta_key VARCHAR(64) PRIMARY KEY, " +
                        "meta_value TEXT NOT NULL" +
                        ");");
            }
        }
    }

    public void close() {
        synchronized (lock) {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    plugin.getLogger().warning("Error closing SQLite connection: " + e.getMessage());
                }
            }
        }
    }

    public boolean isEmpty() {
        synchronized (lock) {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM votify_players;")) {
                if (rs.next()) {
                    return rs.getInt(1) == 0;
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error checking if database is empty: " + e.getMessage());
            }
            return true;
        }
    }

    public void addVote(UUID uuid, String playerName, String serviceName) {
        synchronized (lock) {
            String sql = "INSERT INTO votify_players (uuid, name, total_votes, monthly_votes, weekly_votes, streak, best_streak, best_monthly, best_weekly, wins, voteparty_contribution, last_vote_service, last_vote_time) " +
                    "VALUES (?, ?, 1, 1, 1, 0, 0, 1, 1, 0, 1, ?, ?) " +
                    "ON CONFLICT(uuid) DO UPDATE SET " +
                    "name = excluded.name, " +
                    "total_votes = total_votes + 1, " +
                    "monthly_votes = monthly_votes + 1, " +
                    "weekly_votes = weekly_votes + 1, " +
                    "best_monthly = MAX(best_monthly, monthly_votes + 1), " +
                    "best_weekly = MAX(best_weekly, weekly_votes + 1), " +
                    "voteparty_contribution = voteparty_contribution + 1, " +
                    "last_vote_service = excluded.last_vote_service, " +
                    "last_vote_time = excluded.last_vote_time;";

            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, playerName);
                ps.setString(3, serviceName);
                ps.setLong(4, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Error adding vote in SQLite: " + e.getMessage());
            }
        }
    }

    public Map<String, Object> getPlayerStats(UUID uuid) {
        synchronized (lock) {
            Map<String, Object> stats = new HashMap<>();
            String sql = "SELECT * FROM votify_players WHERE uuid = ?;";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        stats.put("total", rs.getInt("total_votes"));
                        stats.put("monthly", rs.getInt("monthly_votes"));
                        stats.put("weekly", rs.getInt("weekly_votes"));
                        stats.put("streak", rs.getInt("streak"));
                        stats.put("best-streak", rs.getInt("best_streak"));
                        stats.put("best-monthly", rs.getInt("best_monthly"));
                        stats.put("best-weekly", rs.getInt("best_weekly"));
                        stats.put("wins", rs.getInt("wins"));
                        stats.put("voteparty-contribution", rs.getInt("voteparty_contribution"));
                        stats.put("last-vote-service", rs.getString("last_vote_service"));
                        stats.put("last-vote-time", rs.getLong("last_vote_time"));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error fetching player stats: " + e.getMessage());
            }
            return stats;
        }
    }

    public List<Map.Entry<UUID, Integer>> getTopVoters(int limit) {
        return getLeaderboardQuery("SELECT uuid, monthly_votes FROM votify_players WHERE monthly_votes > 0 ORDER BY monthly_votes DESC LIMIT ?;", limit);
    }

    public List<Map.Entry<UUID, Integer>> getWeeklyTopVoters(int limit) {
        return getLeaderboardQuery("SELECT uuid, weekly_votes FROM votify_players WHERE weekly_votes > 0 ORDER BY weekly_votes DESC LIMIT ?;", limit);
    }

    public List<Map.Entry<UUID, Integer>> getAllTimeTopVoters(int limit) {
        return getLeaderboardQuery("SELECT uuid, total_votes FROM votify_players WHERE total_votes > 0 ORDER BY total_votes DESC LIMIT ?;", limit);
    }

    private List<Map.Entry<UUID, Integer>> getLeaderboardQuery(String sql, int limit) {
        synchronized (lock) {
            List<Map.Entry<UUID, Integer>> list = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        try {
                            UUID u = UUID.fromString(rs.getString(1));
                            int votes = rs.getInt(2);
                            list.add(new AbstractMap.SimpleEntry<>(u, votes));
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error querying leaderboard: " + e.getMessage());
            }
            return list;
        }
    }

    public int getTotalServerVotes() {
        synchronized (lock) {
            String sql = "SELECT COALESCE(SUM(total_votes), 0) FROM votify_players;";
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error querying total votes: " + e.getMessage());
            }
            return 0;
        }
    }

    public void addPendingReward(UUID uuid, String rewardString) {
        synchronized (lock) {
            String sql = "INSERT INTO votify_queue (uuid, reward_string) VALUES (?, ?);";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, rewardString);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Error adding pending reward: " + e.getMessage());
            }
        }
    }

    public void addPendingRewards(UUID uuid, List<String> rewardStrings) {
        synchronized (lock) {
            String sql = "INSERT INTO votify_queue (uuid, reward_string) VALUES (?, ?);";
            try {
                connection.setAutoCommit(false);
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    for (String r : rewardStrings) {
                        ps.setString(1, uuid.toString());
                        ps.setString(2, r);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                connection.commit();
                connection.setAutoCommit(true);
            } catch (SQLException e) {
                try {
                    connection.rollback();
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {}
                plugin.getLogger().severe("Error batch adding pending rewards: " + e.getMessage());
            }
        }
    }

    public List<String> getPendingRewards(UUID uuid) {
        synchronized (lock) {
            List<String> list = new ArrayList<>();
            String sql = "SELECT reward_string FROM votify_queue WHERE uuid = ? ORDER BY id ASC;";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(rs.getString("reward_string"));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error querying pending rewards: " + e.getMessage());
            }
            return list;
        }
    }

    public void clearPendingRewards(UUID uuid) {
        synchronized (lock) {
            String sql = "DELETE FROM votify_queue WHERE uuid = ?;";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Error clearing pending rewards: " + e.getMessage());
            }
        }
    }

    public void storeUnclaimedReward(String monthKey, UUID uuid, int rank) {
        synchronized (lock) {
            String sql = "INSERT INTO votify_unclaimed (month_key, uuid, rank, timestamp) VALUES (?, ?, ?, ?);";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, monthKey);
                ps.setString(2, uuid.toString());
                ps.setInt(3, rank);
                ps.setLong(4, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Error storing unclaimed reward: " + e.getMessage());
            }
        }
    }

    public int getUnclaimedRewardRank(UUID uuid) {
        synchronized (lock) {
            String sql = "SELECT id, month_key, rank, timestamp FROM votify_unclaimed WHERE uuid = ? ORDER BY id DESC LIMIT 1;";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int id = rs.getInt("id");
                        int rank = rs.getInt("rank");
                        long ts = rs.getLong("timestamp");
                        // 28 days expiration check
                        if (System.currentTimeMillis() - ts > 2419200000L) {
                            try (PreparedStatement del = connection.prepareStatement("DELETE FROM votify_unclaimed WHERE id = ?;")) {
                                del.setInt(1, id);
                                del.executeUpdate();
                            }
                            return -1;
                        }
                        return rank;
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error fetching unclaimed reward: " + e.getMessage());
            }
            return -1;
        }
    }

    public void removeUnclaimedReward(UUID uuid) {
        synchronized (lock) {
            String sql = "DELETE FROM votify_unclaimed WHERE uuid = ?;";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Error removing unclaimed reward: " + e.getMessage());
            }
        }
    }

    public void processMonthlyReset(String monthKey, List<Map.Entry<UUID, Integer>> topVoters) {
        synchronized (lock) {
            try {
                connection.setAutoCommit(false);

                // 1. Calculate total server votes for this month
                int monthlyTotal = 0;
                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT COALESCE(SUM(monthly_votes), 0) FROM votify_players;")) {
                    if (rs.next()) {
                        monthlyTotal = rs.getInt(1);
                    }
                }
                setMeta("history_total_" + monthKey, String.valueOf(monthlyTotal));

                // 2. Insert Top 10 into history
                String histSql = "INSERT INTO votify_history (month_key, rank, uuid, votes) VALUES (?, ?, ?, ?);";
                try (PreparedStatement ps = connection.prepareStatement(histSql)) {
                    for (int i = 0; i < Math.min(topVoters.size(), 10); i++) {
                        Map.Entry<UUID, Integer> entry = topVoters.get(i);
                        ps.setString(1, monthKey);
                        ps.setInt(2, i + 1);
                        ps.setString(3, entry.getKey().toString());
                        ps.setInt(4, entry.getValue());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                // 3. Top 1 gets +1 win
                if (!topVoters.isEmpty()) {
                    String winSql = "UPDATE votify_players SET wins = wins + 1 WHERE uuid = ?;";
                    try (PreparedStatement ps = connection.prepareStatement(winSql)) {
                        ps.setString(1, topVoters.get(0).getKey().toString());
                        ps.executeUpdate();
                    }
                }

                // 4. Update streaks and reset monthly_votes
                String streakSql = "UPDATE votify_players SET " +
                        "streak = CASE WHEN monthly_votes > 0 THEN streak + 1 ELSE 0 END, " +
                        "best_streak = CASE WHEN monthly_votes > 0 AND (streak + 1) > best_streak THEN streak + 1 ELSE best_streak END, " +
                        "monthly_votes = 0;";
                try (Statement stmt = connection.createStatement()) {
                    stmt.executeUpdate(streakSql);
                }

                connection.commit();
                connection.setAutoCommit(true);
            } catch (SQLException e) {
                try {
                    connection.rollback();
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {}
                plugin.getLogger().severe("Error processing monthly reset in SQLite: " + e.getMessage());
            }
        }
    }

    public void processWeeklyReset() {
        synchronized (lock) {
            String sql = "UPDATE votify_players SET weekly_votes = 0;";
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate(sql);
            } catch (SQLException e) {
                plugin.getLogger().severe("Error resetting weekly votes in SQLite: " + e.getMessage());
            }
        }
    }

    public int getLastMonthTotal(String monthKey) {
        synchronized (lock) {
            String val = getMeta("history_total_" + monthKey, null);
            if (val != null) {
                try {
                    return Integer.parseInt(val);
                } catch (NumberFormatException ignored) {}
            }
            // Fallback: sum from history table
            String sql = "SELECT COALESCE(SUM(votes), 0) FROM votify_history WHERE month_key = ?;";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, monthKey);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error fetching last month total from SQLite: " + e.getMessage());
            }
            return 0;
        }
    }

    public void setMeta(String key, String value) {
        synchronized (lock) {
            String sql = "INSERT INTO votify_metadata (meta_key, meta_value) VALUES (?, ?) " +
                    "ON CONFLICT(meta_key) DO UPDATE SET meta_value = excluded.meta_value;";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, key);
                ps.setString(2, value);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Error setting metadata in SQLite: " + e.getMessage());
            }
        }
    }

    public String getMeta(String key, String def) {
        synchronized (lock) {
            String sql = "SELECT meta_value FROM votify_metadata WHERE meta_key = ?;";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, key);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("meta_value");
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error getting metadata from SQLite: " + e.getMessage());
            }
            return def;
        }
    }

    public int getMetaInt(String key, int def) {
        String val = getMeta(key, null);
        if (val != null) {
            try {
                return Integer.parseInt(val);
            } catch (NumberFormatException ignored) {}
        }
        return def;
    }

    public void setMetaInt(String key, int val) {
        setMeta(key, String.valueOf(val));
    }

    public void insertMigratedPlayer(UUID uuid, String name, int total, int monthly, int weekly,
                                     int streak, int bestStreak, int bestMonthly, int bestWeekly,
                                     int wins, int contribution, String lastService, long lastTime) {
        synchronized (lock) {
            String sql = "INSERT OR REPLACE INTO votify_players " +
                    "(uuid, name, total_votes, monthly_votes, weekly_votes, streak, best_streak, best_monthly, best_weekly, wins, voteparty_contribution, last_vote_service, last_vote_time) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, name);
                ps.setInt(3, total);
                ps.setInt(4, monthly);
                ps.setInt(5, weekly);
                ps.setInt(6, streak);
                ps.setInt(7, bestStreak);
                ps.setInt(8, bestMonthly);
                ps.setInt(9, bestWeekly);
                ps.setInt(10, wins);
                ps.setInt(11, contribution);
                ps.setString(12, lastService);
                ps.setLong(13, lastTime);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Error migrating player to SQLite: " + e.getMessage());
            }
        }
    }
}
