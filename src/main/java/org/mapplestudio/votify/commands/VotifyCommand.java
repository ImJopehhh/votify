package org.mapplestudio.votify.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.mapplestudio.votify.Votify;
import org.mapplestudio.votify.gui.PlayerGui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class VotifyCommand implements CommandExecutor, TabCompleter {

    private final Votify plugin;

    public VotifyCommand(Votify plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player) {
                new PlayerGui(plugin, (Player) sender, PlayerGui.GuiType.MAIN).open();
            } else {
                sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            }
            return true;
        }

        String subCommand = args[0].toLowerCase();
        
        if (subCommand.equals("reload")) {
            if (sender.hasPermission("votify.admin")) {
                plugin.reloadConfig();
                plugin.reloadVoteRewardsConfig();
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.prefix") + " " + plugin.getConfig().getString("messages.reload")));
            } else {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("messages.prefix") + " " + plugin.getConfig().getString("messages.no-permission")));
            }
            return true;
        } else if (subCommand.equals("help")) {
             sendHelpMessage(sender);
             return true;
        } else if (subCommand.equals("leaderboard") || subCommand.equals("topvoter")) {
            if (sender instanceof Player) {
                new PlayerGui(plugin, (Player) sender, PlayerGui.GuiType.LEADERBOARD).open();
            } else {
                sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            }
            return true;
        }

        // Default to opening GUI if argument is unknown but sender is player
        if (sender instanceof Player) {
            new PlayerGui(plugin, (Player) sender, PlayerGui.GuiType.MAIN).open();
        } else {
            sendHelpMessage(sender);
        }
        return true;
    }

    private void sendHelpMessage(CommandSender sender) {
        String prefix = plugin.getConfig().getString("messages.prefix");
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', prefix + "&bVotify Commands:"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/votify &7- Open the Vote Menu."));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/votify leaderboard &7- Open the Top Voter Leaderboard."));
        if (sender.hasPermission("votify.admin")) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/votify reload &7- Reloads the configuration."));
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/votifyadmin &7- Admin commands."));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            completions.add("help");
            completions.add("leaderboard");
            completions.add("topvoter");
            if (sender.hasPermission("votify.admin")) {
                completions.add("reload");
            }
            return completions;
        }
        return Collections.emptyList();
    }
}
