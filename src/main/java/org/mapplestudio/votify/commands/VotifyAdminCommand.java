package org.mapplestudio.votify.commands;

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
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("votify.admin")) {
            player.sendMessage(plugin.getConfig().getString("messages.prefix") + plugin.getConfig().getString("messages.no-permission"));
            return true;
        }

        RewardEditor gui = new RewardEditor(plugin);
        gui.openInventory(player);

        return true;
    }
}
