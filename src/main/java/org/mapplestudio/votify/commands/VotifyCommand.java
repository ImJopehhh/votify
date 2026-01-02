package org.mapplestudio.votify.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.mapplestudio.votify.Votify;
import org.mapplestudio.votify.gui.PlayerGui;

public class VotifyCommand implements CommandExecutor {

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
        if (sender.hasPermission("votify.admin")) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/votify reload &7- Reloads the configuration."));
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', "&e/votifyadmin &7- Admin commands."));
        }
    }
}
