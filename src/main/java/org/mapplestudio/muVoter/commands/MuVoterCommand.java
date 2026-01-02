package org.mapplestudio.muVoter.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.mapplestudio.muVoter.MuVoter;

public class MuVoterCommand implements CommandExecutor {

    private final MuVoter plugin;

    public MuVoterCommand(MuVoter plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelpMessage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        if (subCommand.equals("reload")) {
            if (sender.hasPermission("muvoter.admin")) {
                plugin.reloadConfig();
                plugin.reloadVoteRewardsConfig();
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.prefix") + " " + plugin.getConfig().getString("messages.reload")));
            } else {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.prefix") + " " + plugin.getConfig().getString("messages.no-permission")));
            }
            return true;
        }

        sendHelpMessage(sender);
        return true;
    }

    private void sendHelpMessage(CommandSender sender) {
        String prefix = plugin.getConfig().getString("messages.prefix");
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', prefix + "&bMuVoter Commands:"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/muvoter &7- Shows this help message."));
        if (sender.hasPermission("muvoter.admin")) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/muvoter reload &7- Reloads the configuration."));
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/muvoteradmin &7- Admin commands."));
        }
    }
}
