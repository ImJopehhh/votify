package org.mapplestudio.votify.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.mapplestudio.votify.Votify;
import org.mapplestudio.votify.gui.RewardEditor;

public class VotifyAdminCommand implements CommandExecutor {

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
                // Updated to pass playerName as the third argument
                plugin.getVoteListener().processVote(target, serviceName, playerName);
                sender.sendMessage(ChatColor.GREEN + "Simulated vote for " + playerName + " from " + serviceName);
                break;

            case "rewardsettings":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("This command can only be used by players.");
                    return true;
                }
                RewardEditor gui = new RewardEditor(plugin);
                gui.openInventory((Player) sender);
                break;

            default:
                sendHelpMessage(sender);
                break;
        }

        return true;
    }

    private void sendHelpMessage(CommandSender sender) {
        String prefix = plugin.getConfig().getString("messages.prefix");
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', prefix + "&bVotify Admin Commands:"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/votifyadmin testvote <player> <servicename> &7- Simulate a vote."));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/votifyadmin rewardsettings &7- Open the reward editor GUI."));
    }
}
