package org.mapplestudio.votify.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.mapplestudio.votify.Votify;

import java.util.*;

public class VotifyAdminCommand implements CommandExecutor, TabCompleter {

    private final Votify plugin;

    public VotifyAdminCommand(Votify plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("votify.admin")) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.prefix") + plugin.getConfig().getString("messages.no-permission")));
            return true;
        }

        if (args.length == 0) {
            sendHelpMessage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "testvote":
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /votifyadmin testvote <player> <servicename>");
                    return true;
                }
                String playerName = args[1];
                String serviceName = args[2];
                OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
                plugin.getVoteListener().processVote(target, serviceName, playerName);
                sender.sendMessage(ChatColor.GREEN + "Simulated vote for " + playerName + " from " + serviceName);
                break;

            case "topvoter":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /votifyadmin topvoter <leaderboard|givereward|rewards>");
                    return true;
                }
                handleTopVoterCommand(sender, args[1].toLowerCase());
                break;

            case "reload":
                plugin.reloadConfig();
                plugin.reloadVoteRewardsConfig();
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.prefix") + " " + plugin.getConfig().getString("messages.reload")));
                break;

            case "rewardsettings":
                sender.sendMessage(ChatColor.RED + "This feature is currently disabled.");
                break;

            default:
                sendHelpMessage(sender);
                break;
        }

        return true;
    }

    private void handleTopVoterCommand(CommandSender sender, String arg) {
        switch (arg) {
            case "leaderboard":
                showLeaderboard(sender);
                break;
            case "givereward":
                giveTopVoterRewards(sender);
                break;
            case "rewards":
                showRewards(sender);
                break;
            default:
                sender.sendMessage(ChatColor.RED + "Unknown argument. Use: leaderboard, givereward, rewards");
        }
    }

    private void showLeaderboard(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Monthly Top Voters ===");
        List<Map.Entry<UUID, Integer>> topVoters = plugin.getVoteDataHandler().getTopVoters();
        
        if (topVoters.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "No votes recorded this month.");
            return;
        }

        for (int i = 0; i < Math.min(topVoters.size(), 10); i++) {
            Map.Entry<UUID, Integer> entry = topVoters.get(i);
            OfflinePlayer p = Bukkit.getOfflinePlayer(entry.getKey());
            String name = p.getName() != null ? p.getName() : "Unknown";
            sender.sendMessage(ChatColor.YELLOW + "#" + (i + 1) + " " + ChatColor.WHITE + name + ChatColor.GRAY + " - " + ChatColor.AQUA + entry.getValue() + " votes");
        }
    }

    private void giveTopVoterRewards(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Making top voter rewards available for manual claim...");
        sender.sendMessage(ChatColor.RED + "This should normally happen automatically at the end of the month.");
        
        List<Map.Entry<UUID, Integer>> topVoters = plugin.getVoteDataHandler().getTopVoters();
        if (topVoters.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "No top voters found to reward.");
            return;
        }

        int count = plugin.getVoteDataHandler().distributeTopVoterRewards(topVoters);
        sender.sendMessage(ChatColor.GREEN + "Rewards are now available for claim by " + count + " players.");
    }

    private void showRewards(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Configured Top Voter Rewards ===");
        ConfigurationSection topRewards = plugin.getVoteRewardsConfig().getConfigurationSection("topvoterrewards");
        if (topRewards == null) {
            sender.sendMessage(ChatColor.RED + "No rewards configured.");
            return;
        }

        for (String key : topRewards.getKeys(false)) {
            List<String> rewards = topRewards.getStringList(key);
            sender.sendMessage(ChatColor.YELLOW + "Rank(s) [" + key + "]:");
            for (String reward : rewards) {
                sender.sendMessage(ChatColor.GRAY + " - " + reward);
            }
        }
    }

    private void sendHelpMessage(CommandSender sender) {
        String prefix = plugin.getConfig().getString("messages.prefix");
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', prefix + "&bVotify Admin Commands:"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/votifyadmin testvote <player> <servicename> &7- Simulate a vote."));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/votifyadmin topvoter <leaderboard|givereward|rewards> &7- Manage top voters."));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/votifyadmin reload &7- Reload configuration."));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("votify.admin")) return Collections.emptyList();

        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            completions.add("testvote");
            completions.add("topvoter");
            completions.add("reload");
            return completions;
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("topvoter")) {
                List<String> completions = new ArrayList<>();
                completions.add("leaderboard");
                completions.add("givereward");
                completions.add("rewards");
                return completions;
            } else if (args[0].equalsIgnoreCase("testvote")) {
                return null; // Return null to use default player list
            }
        }
        return Collections.emptyList();
    }
}
