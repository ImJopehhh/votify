package org.mapplestudio.votify.placeholders;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.mapplestudio.votify.Votify;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
        // Handle non-player specific placeholders first (Global Placeholders)
        if (params.startsWith("topvoter_")) {
            // Format: topvoter_<monthOffset>_<position>_<type>
            // OR: topvoter_alltime_<position>_<type>
            
            String[] parts = params.split("_");
            
            // All-time Top Voter
            if (parts.length >= 4 && parts[1].equals("alltime")) {
                try {
                    int position = Integer.parseInt(parts[2]); // 1-based index
                    String type = parts[3].toLowerCase();
                    
                    List<Map.Entry<UUID, Integer>> allTimeTop = plugin.getVoteDataHandler().getAllTimeTopVoters();
                    if (position > allTimeTop.size()) {
                        return type.equals("votes") ? "0" : "None";
                    }
                    
                    Map.Entry<UUID, Integer> entry = allTimeTop.get(position - 1);
                    if (type.equals("name")) {
                        OfflinePlayer p = Bukkit.getOfflinePlayer(entry.getKey());
                        return p.getName() != null ? p.getName() : "Unknown";
                    } else if (type.equals("votes")) {
                        return String.valueOf(entry.getValue());
                    }
                } catch (NumberFormatException e) {
                    return "Error";
                }
            }
            
            // Monthly/Historical Top Voter
            if (parts.length >= 4) {
                try {
                    int monthOffset = Integer.parseInt(parts[1]);
                    int position = Integer.parseInt(parts[2]); // 1-based index
                    String type = parts[3].toLowerCase();

                    if (monthOffset == 0) {
                        // Current month (realtime)
                        List<Map.Entry<UUID, Integer>> topVoters = plugin.getVoteDataHandler().getTopVoters();
                        if (position > topVoters.size()) {
                            return type.equals("votes") ? "0" : "None";
                        }
                        
                        Map.Entry<UUID, Integer> entry = topVoters.get(position - 1);
                        if (type.equals("name")) {
                            OfflinePlayer p = Bukkit.getOfflinePlayer(entry.getKey());
                            return p.getName() != null ? p.getName() : "Unknown";
                        } else if (type.equals("votes")) {
                            return String.valueOf(entry.getValue());
                        }

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

        // If the placeholder requires a player but none is provided (e.g. global hologram), return null
        if (player == null) {
            return null;
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
