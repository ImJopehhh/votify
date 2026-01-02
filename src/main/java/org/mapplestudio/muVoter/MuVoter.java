package org.mapplestudio.muVoter;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.mapplestudio.muVoter.commands.MuVoterAdminCommand;
import org.mapplestudio.muVoter.commands.MuVoterCommand;
import org.mapplestudio.muVoter.data.VoteDataHandler;
import org.mapplestudio.muVoter.listeners.GuiListener;
import org.mapplestudio.muVoter.listeners.VoteListener;
import org.mapplestudio.muVoter.placeholders.MuVoterExpansion;

import java.io.File;
import java.io.IOException;

public final class MuVoter extends JavaPlugin {

    private static MuVoter instance;
    private FileConfiguration voteRewardsConfig;
    private File voteRewardsFile;
    private VoteDataHandler voteDataHandler;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        createVoteRewardsConfig();

        // Data
        this.voteDataHandler = new VoteDataHandler(this);

        // Listeners
        getServer().getPluginManager().registerEvents(new VoteListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(), this);

        // Commands
        getCommand("muvoter").setExecutor(new MuVoterCommand(this));
        getCommand("muvoteradmin").setExecutor(new MuVoterAdminCommand(this));

        // PlaceholderAPI
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new MuVoterExpansion(this).register();
            getLogger().info("Successfully hooked into PlaceholderAPI!");
        }

        getLogger().info("MuVoter has been enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("MuVoter has been disabled!");
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

    public static MuVoter getInstance() {
        return instance;
    }
}
