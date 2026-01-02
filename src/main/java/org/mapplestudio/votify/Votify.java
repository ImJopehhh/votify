package org.mapplestudio.votify;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.mapplestudio.votify.commands.VotifyAdminCommand;
import org.mapplestudio.votify.commands.VotifyCommand;
import org.mapplestudio.votify.data.VoteDataHandler;
import org.mapplestudio.votify.listeners.GuiListener;
import org.mapplestudio.votify.listeners.VoteListener;
import org.mapplestudio.votify.placeholders.VotifyExpansion;

import java.io.File;
import java.io.IOException;

public final class Votify extends JavaPlugin {

    private static Votify instance;
    private FileConfiguration voteRewardsConfig;
    private File voteRewardsFile;
    private VoteDataHandler voteDataHandler;
    private VoteListener voteListener;

    @Override
    public void onEnable() {
        instance = this;

        // Configuration
        saveDefaultConfig();
        createVoteRewardsConfig();

        // Data
        this.voteDataHandler = new VoteDataHandler(this);

        // Listeners
        this.voteListener = new VoteListener(this);
        getServer().getPluginManager().registerEvents(voteListener, this);
        getServer().getPluginManager().registerEvents(new GuiListener(), this);

        // Commands
        getCommand("votify").setExecutor(new VotifyCommand(this));
        getCommand("votifyadmin").setExecutor(new VotifyAdminCommand(this));

        // PlaceholderAPI
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new VotifyExpansion(this).register();
            getLogger().info("Successfully hooked into PlaceholderAPI!");
        }

        getLogger().info("Votify has been enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Votify has been disabled!");
    }

    private void createVoteRewardsConfig() {
        voteRewardsFile = new File(getDataFolder(), "voterewards.yml");
        if (!voteRewardsFile.exists()) {
            saveResource("voterewards.yml", false);
        }
        voteRewardsConfig = YamlConfiguration.loadConfiguration(voteRewardsFile);
    }

    public FileConfiguration getVoteRewardsConfig() {
        return voteRewardsConfig;
    }

    public void reloadVoteRewardsConfig() {
        if (voteRewardsFile == null) {
            voteRewardsFile = new File(getDataFolder(), "voterewards.yml");
        }
        voteRewardsConfig = YamlConfiguration.loadConfiguration(voteRewardsFile);
        reloadConfig();
    }

    public void saveVoteRewardsConfig() {
        try {
            voteRewardsConfig.save(voteRewardsFile);
        } catch (IOException e) {
            getLogger().severe("Could not save voterewards.yml!");
            e.printStackTrace();
        }
    }

    public VoteDataHandler getVoteDataHandler() {
        return voteDataHandler;
    }

    public VoteListener getVoteListener() {
        return voteListener;
    }

    public static Votify getInstance() {
        return instance;
    }
}
