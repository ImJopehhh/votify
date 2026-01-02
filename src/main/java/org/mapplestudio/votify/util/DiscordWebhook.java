package org.mapplestudio.votify.util;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class DiscordWebhook {

    private final String url;
    private String content;
    private EmbedObject embed;

    public DiscordWebhook(String url) {
        this.url = url;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void addEmbed(EmbedObject embed) {
        this.embed = embed;
    }

    public void execute() {
        if (this.content == null && this.embed == null) {
            throw new IllegalArgumentException("Set content or add at least one embed");
        }

        try {
            JSONObject json = new JSONObject();
            json.put("content", this.content);
            if (this.embed != null) {
                JSONArray embeds = new JSONArray();
                JSONObject embedJson = new JSONObject();
                embedJson.put("title", this.embed.getTitle());
                embedJson.put("description", this.embed.getDescription());
                embedJson.put("color", this.embed.getColor());
                if (this.embed.getFooter() != null) {
                    JSONObject footerJson = new JSONObject();
                    footerJson.put("text", this.embed.getFooter());
                    embedJson.put("footer", footerJson);
                }
                embeds.add(embedJson);
                json.put("embeds", embeds);
            }

            URL url = new URL(this.url);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.addRequestProperty("Content-Type", "application/json");
            connection.addRequestProperty("User-Agent", "Java-DiscordWebhook-Votify");
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");

            try (OutputStream stream = connection.getOutputStream()) {
                stream.write(json.toString().getBytes(StandardCharsets.UTF_8));
                stream.flush();
            }

            connection.getInputStream().close();
            connection.disconnect();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static class EmbedObject {
        private String title;
        private String description;
        private int color;
        private String footer;

        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public int getColor() { return color; }
        public String getFooter() { return footer; }

        public EmbedObject setTitle(String title) { this.title = title; return this; }
        public EmbedObject setDescription(String description) { this.description = description; return this; }
        public EmbedObject setColor(int color) { this.color = color; return this; }
        public EmbedObject setFooter(String text) { this.footer = text; return this; }
    }

    // Simple JSON implementation to avoid external dependencies
    private static class JSONObject {
        private final StringBuilder builder = new StringBuilder();

        public JSONObject() { builder.append("{"); }

        public void put(String key, Object value) {
            if (builder.length() > 1) builder.append(",");
            builder.append("\"").append(key).append("\":");
            if (value instanceof String) builder.append("\"").append(escape((String) value)).append("\"");
            else if (value instanceof Integer) builder.append(value);
            else if (value instanceof JSONArray) builder.append(value.toString());
            else if (value instanceof JSONObject) builder.append(value.toString());
            else builder.append("null");
        }

        @Override
        public String toString() { return builder.append("}").toString(); }

        private String escape(String s) {
            return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
        }
    }

    private static class JSONArray {
        private final StringBuilder builder = new StringBuilder();

        public JSONArray() { builder.append("["); }

        public void add(Object value) {
            if (builder.length() > 1) builder.append(",");
            if (value instanceof JSONObject) builder.append(value.toString());
        }

        @Override
        public String toString() { return builder.append("]").toString(); }
    }
}
