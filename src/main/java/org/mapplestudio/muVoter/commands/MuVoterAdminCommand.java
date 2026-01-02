package org.mapplestudio.muVoter.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.mapplestudio.muVoter.MuVoter;
import org.mapplestudio.muVoter.gui.RewardEditor;

public class MuVoterAdminCommand implements CommandExecutor {

    private final MuVoter plugin;

    public MuVoterAdminCommand(MuVoter plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("muvoter.admin")) {
            player.sendMessage(plugin.getConfig().getString("messages.prefix") + plugin.getConfig().getString("messages.no-permission"));
            return true;
        }

        RewardEditor gui = new RewardEditor(plugin);
        gui.openInventory(player);

        return true;
    }
}
