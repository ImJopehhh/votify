package org.mapplestudio.muVoter.placeholders;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.mapplestudio.muVoter.MuVoter;

public class MuVoterExpansion extends PlaceholderExpansion {

    private final MuVoter plugin;

    public MuVoterExpansion(MuVoter plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "muvoter";
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
            case "last_vote_service":
                return plugin.getVoteDataHandler().getVoteData().getString(path + ".last-vote-service", "N/A");
            default:
                return null;
        }
    }
}
