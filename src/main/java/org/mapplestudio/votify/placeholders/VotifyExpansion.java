package org.mapplestudio.votify.placeholders;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.mapplestudio.votify.Votify;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
        // Handle non-player specific placeholders first
        if (params.startsWith("topvoter_")) {
            // Format: topvoter_<monthOffset>_<position>_<type>
            // type: name, votes
            // monthOffset: 0 (current), 1 (last month), etc.
            // Example: topvoter_1_1_name (Top 1 name from 1 month ago)
            
            String[] parts = params.split("_");
            if (parts.length >= 4) {
                try {
                    int monthOffset = Integer.parseInt(parts[1]);
                    int position = Integer.parseInt(parts[2]);
                    String type = parts[3].toLowerCase();

                    if (monthOffset == 0) {
                        // Current month (realtime)
                        // This is expensive to calculate every time, ideally should be cached
                        // For now, we'll just return "Calculating..." or implement a simple cache in DataHandler
                        return "N/A"; 
                    } else {
                        // Historical data
                        String monthKey = LocalDate.now().minusMonths(monthOffset).format(DateTimeFormatter.ofPattern("yyyy-MM"));
                        String path = "history." + monthKey + "." + position;
                        
                        if (type.equals("name")) {
                            String uuidStr = plugin.getVoteDataHandler().getVoteData().getString(path + ".uuid");
                            if (uuidStr != null) {
                                OfflinePlayer p = Bukkit.getOfflinePlayer(UUID.fromString(uuidStr));
                                return p.getName() != null ? p.getName() : "Unknown";
                            }
                            return "None";
                        } else if (type.equals("votes")) {
                            return String.valueOf(plugin.getVoteDataHandler().getVoteData().getInt(path + ".votes", 0));
                        }
                    }
                } catch (NumberFormatException e) {
                    return "Error";
                }
            }
        }

        if (player == null) {
            return "";
        }

        String path = "players." + player.getUniqueId().toString();

        switch (params.toLowerCase()) {
            case "total_votes":
                return String.valueOf(plugin.getVoteDataHandler().getVoteData().getInt(path + ".total", 0));
            case "monthly_votes":
                return String.valueOf(plugin.getVoteDataHandler().getVoteData().getInt(path + ".monthly", 0));
            case "weekly_votes":
                return String.valueOf(plugin.getVoteDataHandler().getVoteData().getInt(path + ".weekly", 0));
            case "wins":
                return String.valueOf(plugin.getVoteDataHandler().getVoteData().getInt(path + ".wins", 0));
            case "last_vote_service":
                return plugin.getVoteDataHandler().getVoteData().getString(path + ".last-vote-service", "N/A");
            default:
                return null;
        }
    }
}
