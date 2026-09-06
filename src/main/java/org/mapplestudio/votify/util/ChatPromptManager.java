package org.mapplestudio.votify.util;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChatPromptManager implements Listener {

    public interface PromptCallback {
        void onResponse(Player player, String input);
    }

    private final JavaPlugin plugin;
    private final Map<UUID, PromptCallback> activePrompts = new ConcurrentHashMap<>();

    public ChatPromptManager(JavaPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void startPrompt(Player player, String promptTitle, PromptCallback callback) {
        activePrompts.put(player.getUniqueId(), callback);
        player.closeInventory();
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&8&m----------------------------------------"));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', promptTitle));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7Type your input in chat, or type &c&ncancel&7 to abort."));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&8&m----------------------------------------"));
    }

    public void cancelPrompt(Player player) {
        activePrompts.remove(player.getUniqueId());
        player.sendMessage(ChatColor.RED + "Prompt cancelled.");
    }

    public boolean hasPrompt(Player player) {
        return activePrompts.containsKey(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        PromptCallback callback = activePrompts.remove(player.getUniqueId());

        if (callback != null) {
            event.setCancelled(true);
            String message = event.getMessage().trim();

            if (message.equalsIgnoreCase("cancel")) {
                player.sendMessage(ChatColor.RED + "Operation cancelled.");
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> callback.onResponse(player, message));
        }
    }
}
