package org.mapplestudio.votify.commands;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.mapplestudio.votify.Votify;
import org.mapplestudio.votify.gui.PlayerGui;
import org.mapplestudio.votify.util.ColorUtil;

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
        
        if (subCommand.equals("help")) {
             sendHelpMessage(sender);
             return true;
        } else if (subCommand.equals("sites") || subCommand.equals("links")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                new PlayerGui(plugin, player, PlayerGui.GuiType.SITES).open();
                sendSitesInChat(player);
            } else {
                sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            }
            return true;
        } else if (subCommand.equals("leaderboard") || subCommand.equals("topvoter")) {
            if (sender instanceof Player) {
                new PlayerGui(plugin, (Player) sender, PlayerGui.GuiType.LEADERBOARD).open();
            } else {
                sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            }
            return true;
        } else if (subCommand.equals("claim")) {
            if (sender instanceof Player) {
                new PlayerGui(plugin, (Player) sender, PlayerGui.GuiType.CLAIM).open();
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

    public void sendSitesInChat(Player player) {
        ConfigurationSection sitesSec = plugin.getConfig().getConfigurationSection("vote-sites");
        if (sitesSec == null || sitesSec.getKeys(false).isEmpty()) {
            player.sendMessage(ColorUtil.colorize("&cNo vote sites configured yet."));
            return;
        }

        player.sendMessage(ColorUtil.colorize("&8&m----------------------------------------"));
        player.sendMessage(ColorUtil.colorize("&b&lVote Sites &7(Click to open link in browser):"));

        int num = 1;
        for (String key : sitesSec.getKeys(false)) {
            String name = sitesSec.getString(key + ".name", key);
            String url = sitesSec.getString(key + ".url", "");

            TextComponent message = new TextComponent(ColorUtil.colorize("&e" + num + ". " + name + " "));
            
            TextComponent linkBtn = new TextComponent(ColorUtil.colorize("&a&l[CLICK TO VOTE]"));
            linkBtn.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
            linkBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                    new ComponentBuilder(ColorUtil.colorize("&bClick to open:\n&f" + url)).create()));

            message.addExtra(linkBtn);
            player.spigot().sendMessage(message);
            num++;
        }
        player.sendMessage(ColorUtil.colorize("&8&m----------------------------------------"));
    }

    private void sendHelpMessage(CommandSender sender) {
        String prefix = plugin.getConfig().getString("messages.prefix");
        sender.sendMessage(ColorUtil.colorize(prefix + "&bVotify Commands:"));
        sender.sendMessage(ColorUtil.colorize("&e/votify &7- Open the Vote Menu."));
        sender.sendMessage(ColorUtil.colorize("&e/votify sites &7- View voting websites & links."));
        sender.sendMessage(ColorUtil.colorize("&e/votify leaderboard &7- Open the Top Voter Leaderboard."));
        sender.sendMessage(ColorUtil.colorize("&e/votify claim &7- Claim Top Voter Rewards."));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            completions.add("help");
            completions.add("sites");
            completions.add("links");
            completions.add("leaderboard");
            completions.add("topvoter");
            completions.add("claim");
            return completions;
        }
        return Collections.emptyList();
    }
}
