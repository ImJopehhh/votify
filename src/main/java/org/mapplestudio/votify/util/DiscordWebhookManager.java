package org.mapplestudio.votify.util;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.mapplestudio.votify.Votify;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages all Discord webhook interactions for the Votify plugin.
 * Supports per-vote notifications and monthly top voter announcements
 * with fully configurable embeds via config.yml.
 */
public class DiscordWebhookManager {

    private final Votify plugin;

    public DiscordWebhookManager(Votify plugin) {
        this.plugin = plugin;
    }

    // ======================================================================
    // Public API
    // ======================================================================

    /**
     * Sends a vote notification embed to Discord.
     * Called every time a player votes.
     *
     * @param playerName  The name of the player who voted.
     * @param serviceName The vote service name.
     */
    public void sendVoteNotification(String playerName, String serviceName) {
        if (!plugin.getConfig().getBoolean("discord.vote-webhook.enabled", false)) return;

        String webhookUrl = plugin.getConfig().getString("discord.vote-webhook.webhook-url", "");
        if (webhookUrl == null || webhookUrl.isEmpty() || webhookUrl.contains("...")) return;

        int totalVotes = plugin.getVoteDataHandler().getTotalServerVotes();
        String serverName = plugin.getConfig().getString("server-name", "My Server");

        // Read embed configuration
        String title = resolve(plugin.getConfig().getString("discord.vote-embed.title", "New Vote Received!"),
                playerName, serviceName, null, serverName, totalVotes);
        String description = resolve(plugin.getConfig().getString("discord.vote-embed.description",
                "**%player%** has just voted on **%service%**! Thank you for supporting the server."),
                playerName, serviceName, null, serverName, totalVotes);
        int color = plugin.getConfig().getInt("discord.vote-embed.color", 5763719);
        String footerText = resolve(plugin.getConfig().getString("discord.vote-embed.footer-text", "Votify System"),
                playerName, serviceName, null, serverName, totalVotes);
        String footerIconUrl = plugin.getConfig().getString("discord.vote-embed.footer-icon-url", "");
        String thumbnailUrl = resolve(plugin.getConfig().getString("discord.vote-embed.thumbnail-url",
                "https://mc-heads.net/avatar/%player%/128"), playerName, serviceName, null, serverName, totalVotes);
        boolean showTimestamp = plugin.getConfig().getBoolean("discord.vote-embed.timestamp", true);

        // Build JSON payload
        JSONObject embed = new JSONObject();
        embed.put("title", title);
        embed.put("description", description);
        embed.put("color", color);

        if (footerText != null && !footerText.isEmpty()) {
            JSONObject footer = new JSONObject();
            footer.put("text", footerText);
            if (footerIconUrl != null && !footerIconUrl.isEmpty()) {
                footer.put("icon_url", footerIconUrl);
            }
            embed.putObject("footer", footer);
        }

        if (thumbnailUrl != null && !thumbnailUrl.isEmpty()) {
            JSONObject thumbnail = new JSONObject();
            thumbnail.put("url", thumbnailUrl);
            embed.putObject("thumbnail", thumbnail);
        }

        if (showTimestamp) {
            embed.put("timestamp", Instant.now().toString());
        }

        JSONObject payload = new JSONObject();
        JSONArray embeds = new JSONArray();
        embeds.addObject(embed);
        payload.putArray("embeds", embeds);

        sendAsync(webhookUrl, payload.toString());
    }

    /**
     * Sends the monthly top voter announcement embed to Discord.
     *
     * @param topVoters Sorted list of top voters (UUID -> vote count).
     * @param monthKey  The month identifier (e.g. "2026-03").
     */
    public void sendMonthlyTopVoters(List<Map.Entry<UUID, Integer>> topVoters, String monthKey) {
        if (!plugin.getConfig().getBoolean("discord.top-voter-webhook.enabled", false)) return;

        String webhookUrl = plugin.getConfig().getString("discord.top-voter-webhook.webhook-url", "");
        if (webhookUrl == null || webhookUrl.isEmpty() || webhookUrl.contains("...")) return;

        int totalVotes = plugin.getVoteDataHandler().getTotalServerVotes();
        String serverName = plugin.getConfig().getString("server-name", "My Server");

        // Read embed configuration
        String title = resolve(plugin.getConfig().getString("discord.top-voter-embed.title", "Monthly Top Voters"),
                null, null, monthKey, serverName, totalVotes);
        String description = resolve(plugin.getConfig().getString("discord.top-voter-embed.description",
                "Here are the top voters for **%month%**! Thank you all for your support."),
                null, null, monthKey, serverName, totalVotes);
        int color = plugin.getConfig().getInt("discord.top-voter-embed.color", 16766720);
        String footerText = resolve(plugin.getConfig().getString("discord.top-voter-embed.footer-text", "Votify System"),
                null, null, monthKey, serverName, totalVotes);
        String footerIconUrl = plugin.getConfig().getString("discord.top-voter-embed.footer-icon-url", "");
        String thumbnailUrl = plugin.getConfig().getString("discord.top-voter-embed.thumbnail-url", "");
        String imageUrl = plugin.getConfig().getString("discord.top-voter-embed.image-url", "");
        String entryFormat = plugin.getConfig().getString("discord.top-voter-entry-format",
                "%rank%. **%player%** — %votes% votes");

        // Build leaderboard entries
        StringBuilder leaderboard = new StringBuilder();
        leaderboard.append(description).append("\n\n");

        int limit = Math.min(topVoters.size(), 10);
        for (int i = 0; i < limit; i++) {
            Map.Entry<UUID, Integer> entry = topVoters.get(i);
            OfflinePlayer p = Bukkit.getOfflinePlayer(entry.getKey());
            String name = p.getName() != null ? p.getName() : "Unknown";

            String line = entryFormat
                    .replace("%rank%", String.valueOf(i + 1))
                    .replace("%player%", name)
                    .replace("%votes%", String.valueOf(entry.getValue()));
            leaderboard.append(line).append("\n");
        }

        // Build JSON payload
        JSONObject embed = new JSONObject();
        embed.put("title", title);
        embed.put("description", leaderboard.toString().trim());
        embed.put("color", color);

        if (footerText != null && !footerText.isEmpty()) {
            JSONObject footer = new JSONObject();
            footer.put("text", footerText);
            if (footerIconUrl != null && !footerIconUrl.isEmpty()) {
                footer.put("icon_url", footerIconUrl);
            }
            embed.putObject("footer", footer);
        }

        if (thumbnailUrl != null && !thumbnailUrl.isEmpty()) {
            JSONObject thumbnail = new JSONObject();
            thumbnail.put("url", thumbnailUrl);
            embed.putObject("thumbnail", thumbnail);
        }

        if (imageUrl != null && !imageUrl.isEmpty()) {
            JSONObject image = new JSONObject();
            image.put("url", imageUrl);
            embed.putObject("image", image);
        }

        embed.put("timestamp", Instant.now().toString());

        JSONObject payload = new JSONObject();
        JSONArray embeds = new JSONArray();
        embeds.addObject(embed);
        payload.putArray("embeds", embeds);

        sendAsync(webhookUrl, payload.toString());
    }

    /**
     * Sends a test webhook embed to verify the configuration.
     * Uses the vote-webhook URL for testing.
     *
     * @param sender The command sender who initiated the test.
     */
    public void sendTestWebhook(CommandSender sender) {
        String webhookUrl = plugin.getConfig().getString("discord.vote-webhook.webhook-url", "");
        if (webhookUrl == null || webhookUrl.isEmpty() || webhookUrl.contains("...")) {
            // Fallback to top-voter webhook
            webhookUrl = plugin.getConfig().getString("discord.top-voter-webhook.webhook-url", "");
        }

        if (webhookUrl == null || webhookUrl.isEmpty() || webhookUrl.contains("...")) {
            sender.sendMessage("§c[Votify] No valid webhook URL configured. Please set a webhook URL in config.yml.");
            return;
        }

        String serverName = plugin.getConfig().getString("server-name", "My Server");

        JSONObject embed = new JSONObject();
        embed.put("title", "✅ Votify Webhook Test");
        embed.put("description", "This is a test message from **" + escapeJson(serverName) + "**.\\nIf you can see this, your webhook is working correctly!");
        embed.put("color", 5763719);

        JSONObject footer = new JSONObject();
        footer.put("text", "Votify v" + plugin.getDescription().getVersion());
        embed.putObject("footer", footer);

        embed.put("timestamp", Instant.now().toString());

        JSONObject payload = new JSONObject();
        JSONArray embeds = new JSONArray();
        embeds.addObject(embed);
        payload.putArray("embeds", embeds);

        final String finalUrl = webhookUrl;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean success = sendSync(finalUrl, payload.toString());
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (success) {
                    sender.sendMessage("§a[Votify] Test webhook sent successfully! Check your Discord channel.");
                } else {
                    sender.sendMessage("§c[Votify] Failed to send test webhook. Check console for errors.");
                }
            });
        });
    }

    // ======================================================================
    // Internal Helpers
    // ======================================================================

    /**
     * Replaces all supported placeholders in a string.
     */
    private String resolve(String text, String player, String service, String month, String server, int totalVotes) {
        if (text == null) return "";
        if (player != null) text = text.replace("%player%", player);
        if (service != null) text = text.replace("%service%", service);
        if (month != null) text = text.replace("%month%", month);
        if (server != null) text = text.replace("%server%", server);
        text = text.replace("%total_votes%", String.valueOf(totalVotes));
        return text;
    }

    /**
     * Sends a JSON payload to a webhook URL asynchronously (non-blocking).
     */
    private void sendAsync(String webhookUrl, String jsonPayload) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> sendSync(webhookUrl, jsonPayload));
    }

    /**
     * Sends a JSON payload to a webhook URL synchronously.
     *
     * @return true if the request was successful (HTTP 2xx).
     */
    private boolean sendSync(String webhookUrl, String jsonPayload) {
        try {
            URL url = new URL(webhookUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("User-Agent", "Votify-Webhook");
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            try (OutputStream stream = connection.getOutputStream()) {
                stream.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
                stream.flush();
            }

            int responseCode = connection.getResponseCode();
            connection.disconnect();

            if (responseCode >= 200 && responseCode < 300) {
                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().info("[Webhook] Successfully sent payload (HTTP " + responseCode + ")");
                }
                return true;
            } else {
                plugin.getLogger().warning("[Webhook] Discord returned HTTP " + responseCode);
                return false;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Webhook] Failed to send Discord webhook: " + e.getMessage());
            if (plugin.getConfig().getBoolean("debug", false)) {
                e.printStackTrace();
            }
            return false;
        }
    }

    // ======================================================================
    // Minimal JSON Builder (no external dependencies)
    // ======================================================================

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Simple JSON object builder that produces valid JSON strings.
     */
    static class JSONObject {
        private final StringBuilder sb = new StringBuilder();
        private boolean hasEntry = false;

        public JSONObject() {
            sb.append("{");
        }

        public void put(String key, String value) {
            appendComma();
            sb.append("\"").append(escapeJson(key)).append("\":");
            if (value == null) {
                sb.append("null");
            } else {
                sb.append("\"").append(escapeJson(value)).append("\"");
            }
        }

        public void put(String key, int value) {
            appendComma();
            sb.append("\"").append(escapeJson(key)).append("\":").append(value);
        }

        public void putObject(String key, JSONObject obj) {
            appendComma();
            sb.append("\"").append(escapeJson(key)).append("\":").append(obj.build());
        }

        public void putArray(String key, JSONArray arr) {
            appendComma();
            sb.append("\"").append(escapeJson(key)).append("\":").append(arr.build());
        }

        private void appendComma() {
            if (hasEntry) sb.append(",");
            hasEntry = true;
        }

        public String build() {
            return sb.toString() + "}";
        }

        @Override
        public String toString() {
            return build();
        }
    }

    /**
     * Simple JSON array builder.
     */
    static class JSONArray {
        private final StringBuilder sb = new StringBuilder();
        private boolean hasEntry = false;

        public JSONArray() {
            sb.append("[");
        }

        public void addObject(JSONObject obj) {
            if (hasEntry) sb.append(",");
            hasEntry = true;
            sb.append(obj.build());
        }

        public String build() {
            return sb.toString() + "]";
        }

        @Override
        public String toString() {
            return build();
        }
    }
}
