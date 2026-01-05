package org.mapplestudio.votify.placeholders;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.mapplestudio.votify.Votify;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class VotifyExpansion extends PlaceholderExpansion {

    private final Votify plugin;

    public VotifyExpansion(Votify plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "votify";
    }

    @Override
    public String getAuthor() {
        return "MappleStudio";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        String lowerParams = params.toLowerCase();

        // --- Global Placeholders (No Player Required) ---

        if (lowerParams.equals("total")) {
            return String.valueOf(plugin.getVoteDataHandler().getTotalServerVotes());
        }
        
        if (lowerParams.equals("alltimetotal")) {
             // Assuming "6 months total" as requested, or just all time tracked
             // For now, returning total server votes as "all time"
             return String.valueOf(plugin.getVoteDataHandler().getTotalServerVotes());
        }
        
        if (lowerParams.equals("lastmonthtotal")) {
             // Logic to sum up history from last month
             // This would require iterating history.yyyy-MM.*.votes
             // For simplicity/performance, returning 0 or implementing a specific tracker later
             return "0"; // Placeholder for now
        }

        if (lowerParams.equals("votepartyvotescurrent")) {
            return String.valueOf(plugin.getVoteDataHandler().getVoteData().getInt("voteparty.current", 0));
        }
        
        if (lowerParams.equals("votepartyvotesneeded")) {
            int current = plugin.getVoteDataHandler().getVoteData().getInt("voteparty.current", 0);
            int required = plugin.getVoteRewardsConfig().getInt("voteparty.votes-required", 50);
            return String.valueOf(Math.max(0, required - current));
        }
        
        if (lowerParams.equals("votepartyvotesrequired")) {
            return String.valueOf(plugin.getVoteRewardsConfig().getInt("voteparty.votes-required", 50));
        }
        
        if (lowerParams.equals("timeuntildayreset")) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime tomorrow = now.plusDays(1).with(LocalTime.MIDNIGHT);
            long seconds = ChronoUnit.SECONDS.between(now, tomorrow);
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            return String.format("%02d:%02d", hours, minutes);
        }

        // Top Voter Placeholders
        if (lowerParams.startsWith("top_")) {
            String[] parts = lowerParams.split("_");
            // Format: top_<type>_<number>_<field>
            // Types: all, month, week
            
            if (parts.length >= 4) {
                try {
                    String type = parts[1];
                    int number = Integer.parseInt(parts[2]);
                    String field = parts[3]; // name or votes
                    
                    List<Map.Entry<UUID, Integer>> list = null;
                    if (type.equals("all")) list = plugin.getVoteDataHandler().getAllTimeTopVoters();
                    else if (type.equals("month")) list = plugin.getVoteDataHandler().getTopVoters();
                    else if (type.equals("week")) list = plugin.getVoteDataHandler().getWeeklyTopVoters();
                    
                    if (list != null) {
                        if (number <= list.size()) {
                            Map.Entry<UUID, Integer> entry = list.get(number - 1);
                            if (field.equals("name")) {
                                OfflinePlayer p = Bukkit.getOfflinePlayer(entry.getKey());
                                return p.getName() != null ? p.getName() : "Unknown";
                            } else if (field.equals("votes")) {
                                return String.valueOf(entry.getValue());
                            }
                        } else {
                            return field.equals("votes") ? "0" : "None";
                        }
                    }
                } catch (Exception e) {
                    return "Error";
                }
            }
        }

        // --- Player Specific Placeholders ---
        if (player == null) return null;

        String path = "players." + player.getUniqueId().toString();

        switch (lowerParams) {
            case "total_alltime":
                return String.valueOf(plugin.getVoteDataHandler().getVoteData().getInt(path + ".total", 0));
            case "total_monthly":
                return String.valueOf(plugin.getVoteDataHandler().getVoteData().getInt(path + ".monthly", 0));
            case "total_weekly":
                return String.valueOf(plugin.getVoteDataHandler().getVoteData().getInt(path + ".weekly", 0));
            case "bestweeklytotal":
                return String.valueOf(plugin.getVoteDataHandler().getVoteData().getInt(path + ".best-weekly", 0));
            case "bestmonthlytotal":
                return String.valueOf(plugin.getVoteDataHandler().getVoteData().getInt(path + ".best-monthly", 0));
            case "monthvotestreak":
                return String.valueOf(plugin.getVoteDataHandler().getVoteData().getInt(path + ".streak", 0));
            case "bestmonthvotestreak":
                return String.valueOf(plugin.getVoteDataHandler().getVoteData().getInt(path + ".best-streak", 0));
            case "votepartycontributedvotes":
                return String.valueOf(plugin.getVoteDataHandler().getVoteData().getInt(path + ".voteparty-contribution", 0));
            case "top_all_position":
                return String.valueOf(getRank(player.getUniqueId(), plugin.getVoteDataHandler().getAllTimeTopVoters()));
            case "top_month_position":
                return String.valueOf(getRank(player.getUniqueId(), plugin.getVoteDataHandler().getTopVoters()));
            case "top_week_position":
                return String.valueOf(getRank(player.getUniqueId(), plugin.getVoteDataHandler().getWeeklyTopVoters()));
            default:
                return null;
        }
    }
    
    private int getRank(UUID uuid, List<Map.Entry<UUID, Integer>> list) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getKey().equals(uuid)) {
                return i + 1;
            }
        }
        return 0; // Unranked
    }
}
