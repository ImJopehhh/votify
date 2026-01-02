package org.mapplestudio.votify.data;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.mapplestudio.votify.Votify;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class VoteDataHandler {

    private final Votify plugin;
    private FileConfiguration voteDataConfig;
    private File voteDataFile;

    public VoteDataHandler(Votify plugin) {
        this.plugin = plugin;
        setup();
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
        try {
            voteDataConfig.save(voteDataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save votedata.yml!");
            e.printStackTrace();
        }
    }

    public void reloadVoteData() {
        voteDataConfig = YamlConfiguration.loadConfiguration(voteDataFile);
    }

    public void addVote(UUID playerUUID, String serviceName) {
        String path = "players." + playerUUID.toString();
        int totalVotes = getVoteData().getInt(path + ".total", 0) + 1;
        int monthlyVotes = getVoteData().getInt(path + ".monthly", 0) + 1;
        int weeklyVotes = getVoteData().getInt(path + ".weekly", 0) + 1;

        getVoteData().set(path + ".total", totalVotes);
        getVoteData().set(path + ".monthly", monthlyVotes);
        getVoteData().set(path + ".weekly", weeklyVotes);
        getVoteData().set(path + ".last-vote-service", serviceName);
        getVoteData().set(path + ".last-vote-time", System.currentTimeMillis());

        saveVoteData();
    }
}
