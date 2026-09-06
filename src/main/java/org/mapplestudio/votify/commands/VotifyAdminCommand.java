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
import org.mapplestudio.votify.gui.RewardEditor;
import org.mapplestudio.votify.util.ColorUtil;

import java.util.*;

public class VotifyAdminCommand implements CommandExecutor, TabCompleter {

    private final Votify plugin;

    public VotifyAdminCommand(Votify plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("votify.admin")) {
            sender.sendMessage(plugin.getMessage("no-permission"));
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
                    sender.sendMessage(ColorUtil.colorize("&cUsage: /votifyadmin testvote <player> <servicename>"));
                    return true;
                }
                String playerName = args[1];
                String serviceName = args[2];
                OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
                plugin.getVoteListener().processVote(target, serviceName, playerName);
                sender.sendMessage(ColorUtil.colorize("&aSimulated vote for &e" + playerName + " &afrom &e" + serviceName));
                break;

            case "topvoter":
                if (args.length < 2) {
                    sender.sendMessage(ColorUtil.colorize("&cUsage: /votifyadmin topvoter <leaderboard|givereward|rewards>"));
                    return true;
                }
                handleTopVoterCommand(sender, args[1].toLowerCase());
                break;

            case "party":
                if (args.length < 2) {
                    sender.sendMessage(ColorUtil.colorize("&cUsage: /votifyadmin party <force|add|reset> [amount]"));
                    return true;
                }
                String action = args[1].toLowerCase();
                if (action.equals("force")) {
                    plugin.getVoteDataHandler().triggerVoteParty();
                    sender.sendMessage(ColorUtil.colorize("&aForced Vote Party triggered successfully!"));
                } else if (action.equals("add")) {
                    if (args.length < 3) {
                        sender.sendMessage(ColorUtil.colorize("&cUsage: /votifyadmin party add <amount>"));
                        return true;
                    }
                    try {
                        int amount = Integer.parseInt(args[2]);
                        plugin.getVoteDataHandler().addVotePartyVotes(amount);
                        sender.sendMessage(ColorUtil.colorize("&aAdded &e" + amount + " &avotes to Vote Party progress. Current: &e" + 
                                plugin.getVoteDataHandler().getVotePartyCurrent() + "/" + plugin.getVoteDataHandler().getVotePartyRequired()));
                    } catch (NumberFormatException e) {
                        sender.sendMessage(ColorUtil.colorize("&cAmount must be an integer number!"));
                    }
                } else if (action.equals("reset")) {
                    plugin.getVoteDataHandler().resetVoteParty();
                    sender.sendMessage(ColorUtil.colorize("&aVote Party progress has been reset to 0."));
                } else {
                    sender.sendMessage(ColorUtil.colorize("&cUnknown action. Use: /votifyadmin party <force|add|reset>"));
                }
                break;

            case "setvotes":
                if (args.length < 3) {
                    sender.sendMessage(ColorUtil.colorize("&cUsage: /votifyadmin setvotes <player> <amount> [monthly|weekly|total]"));
                    return true;
                }
                String targetName = args[1];
                int amount;
                try {
                    amount = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ColorUtil.colorize("&cAmount must be a valid integer!"));
                    return true;
                }
                String stat = (args.length >= 4) ? args[3].toLowerCase() : "total";
                if (!stat.equals("monthly") && !stat.equals("weekly") && !stat.equals("total")) {
                    sender.sendMessage(ColorUtil.colorize("&cStat must be: monthly, weekly, or total"));
                    return true;
                }
                OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetName);
                plugin.getVoteDataHandler().setPlayerVotes(targetPlayer.getUniqueId(), stat, amount);
                sender.sendMessage(ColorUtil.colorize("&aSet &e" + stat + " &avotes of &e" + targetName + " &ato &e" + amount + "&a."));
                break;

            case "resetplayer":
                if (args.length < 2) {
                    sender.sendMessage(ColorUtil.colorize("&cUsage: /votifyadmin resetplayer <player>"));
                    return true;
                }
                OfflinePlayer pToReset = Bukkit.getOfflinePlayer(args[1]);
                plugin.getVoteDataHandler().resetPlayerData(pToReset.getUniqueId());
                sender.sendMessage(ColorUtil.colorize("&aSuccessfully reset all voting stats for player: &e" + args[1]));
                break;

            case "reload":
                plugin.reloadConfig();
                plugin.reloadVoteRewardsConfig();
                if (plugin.getBossBar() != null) {
                    plugin.getBossBar().init();
                }
                sender.sendMessage(plugin.getMessage("reload"));
                break;

            case "rewardsettings":
            case "editor":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "This command can only be executed by a player.");
                    return true;
                }
                new RewardEditor(plugin).openInventory((Player) sender);
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
                sender.sendMessage(ColorUtil.colorize("&cUnknown argument. Use: leaderboard, givereward, rewards"));
        }
    }

    private void showLeaderboard(CommandSender sender) {
        sender.sendMessage(ColorUtil.colorize("&6=== Monthly Top Voters ==="));
        List<Map.Entry<UUID, Integer>> topVoters = plugin.getVoteDataHandler().getTopVoters();
        
        if (topVoters.isEmpty()) {
            sender.sendMessage(ColorUtil.colorize("&7No votes recorded this month."));
            return;
        }

        for (int i = 0; i < Math.min(topVoters.size(), 10); i++) {
            Map.Entry<UUID, Integer> entry = topVoters.get(i);
            OfflinePlayer p = Bukkit.getOfflinePlayer(entry.getKey());
            String name = p.getName() != null ? p.getName() : "Unknown";
            sender.sendMessage(ColorUtil.colorize("&e#" + (i + 1) + " &f" + name + " &7- &b" + entry.getValue() + " votes"));
        }
    }

    private void giveTopVoterRewards(CommandSender sender) {
        sender.sendMessage(ColorUtil.colorize("&eMaking top voter rewards available for manual claim..."));
        sender.sendMessage(ColorUtil.colorize("&cThis should normally happen automatically at the end of the month."));
        
        List<Map.Entry<UUID, Integer>> topVoters = plugin.getVoteDataHandler().getTopVoters();
        if (topVoters.isEmpty()) {
            sender.sendMessage(ColorUtil.colorize("&cNo top voters found to reward."));
            return;
        }

        int count = plugin.getVoteDataHandler().distributeTopVoterRewards(topVoters);
        sender.sendMessage(ColorUtil.colorize("&aRewards are now available for claim by " + count + " players."));
    }

    private void showRewards(CommandSender sender) {
        sender.sendMessage(ColorUtil.colorize("&6=== Configured Top Voter Rewards ==="));
        ConfigurationSection topRewards = plugin.getVoteRewardsConfig().getConfigurationSection("topvoterrewards");
        if (topRewards == null) {
            sender.sendMessage(ColorUtil.colorize("&cNo rewards configured."));
            return;
        }

        for (String key : topRewards.getKeys(false)) {
            List<String> rewards = topRewards.getStringList(key);
            sender.sendMessage(ColorUtil.colorize("&eRank(s) [" + key + "]:"));
            for (String reward : rewards) {
                sender.sendMessage(ColorUtil.colorize("&7 - " + reward));
            }
        }
    }

    private void sendHelpMessage(CommandSender sender) {
        String prefix = plugin.getConfig().getString("messages.prefix");
        sender.sendMessage(ColorUtil.colorize(prefix + "&bVotify Admin Commands:"));
        sender.sendMessage(ColorUtil.colorize("&e/votifyadmin testvote <player> <servicename> &7- Simulate a vote."));
        sender.sendMessage(ColorUtil.colorize("&e/votifyadmin party <force|add|reset> &7- Manage vote party event."));
        sender.sendMessage(ColorUtil.colorize("&e/votifyadmin setvotes <player> <amount> [type] &7- Set player votes."));
        sender.sendMessage(ColorUtil.colorize("&e/votifyadmin resetplayer <player> &7- Reset player votes."));
        sender.sendMessage(ColorUtil.colorize("&e/votifyadmin topvoter <leaderboard|givereward|rewards> &7- Manage top voters."));
        sender.sendMessage(ColorUtil.colorize("&e/votifyadmin editor &7- Open in-game reward editor GUI."));
        sender.sendMessage(ColorUtil.colorize("&e/votifyadmin reload &7- Reload configuration."));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("votify.admin")) return Collections.emptyList();

        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            completions.add("testvote");
            completions.add("party");
            completions.add("setvotes");
            completions.add("resetplayer");
            completions.add("topvoter");
            completions.add("editor");
            completions.add("rewardsettings");
            completions.add("reload");
            return completions;
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("party")) {
                List<String> completions = new ArrayList<>();
                completions.add("force");
                completions.add("add");
                completions.add("reset");
                return completions;
            } else if (args[0].equalsIgnoreCase("topvoter")) {
                List<String> completions = new ArrayList<>();
                completions.add("leaderboard");
                completions.add("givereward");
                completions.add("rewards");
                return completions;
            } else if (args[0].equalsIgnoreCase("testvote") || args[0].equalsIgnoreCase("setvotes") || args[0].equalsIgnoreCase("resetplayer")) {
                return null; // Return null to use default player list
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("setvotes")) {
            List<String> completions = new ArrayList<>();
            completions.add("total");
            completions.add("monthly");
            completions.add("weekly");
            return completions;
        }
        return Collections.emptyList();
    }
}
