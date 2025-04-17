package org.passtoast.discordbot;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.FileUpload;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

public class RedditListener extends ListenerAdapter {

    private static final long MAX_UPLOAD = 8L * 1024 * 1024; // 8 MiB

    public void onMessageReceived(MessageReceivedEvent event){
        // If the chat message is made by another bot, return
        if (event.getAuthor().isBot()) return;

        String msg = event.getMessage().getContentRaw();
        if(!msg.contains("reddit.com/r/") && !msg.contains("redd.it/"))
            return;

        JsonObject data = postData(msg);
        String mediaUrl = getMedia(data);

        if(mediaUrl == null || mediaUrl.isBlank()) {
            event.getChannel().sendMessage("Media retrieval failed. This sucks, now I need to get my brains checked again.").queue();
            return;
        }

        event.getChannel().sendMessage(mediaUrl).queue(
                null,
                error   -> {
                    System.err.println("Failed to post link:");
                    error.printStackTrace();
                });
        return;
    }

    private JsonObject postData(String redditUrl) {
        try{
            String jsonUrl = redditUrl;
            if (!redditUrl.endsWith(".json")) {
                jsonUrl = redditUrl.split("\\?")[0];
                if (!jsonUrl.endsWith("/")) {
                    jsonUrl += "/";
                }
                jsonUrl += ".json";
            }

            jsonUrl = jsonUrl.replaceFirst("^https?://(www\\.)?reddit\\.com", "https://oauth.reddit.com");

            // Fetch the JSON
            HttpClient client = HttpClient.newHttpClient();
            String bearer = getRedditBearer(client);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(jsonUrl))
                    .header("User-Agent", "java:org.passtoast.discordbot:v1.0")
                    .header("Authorization", "Bearer " + bearer)
                    .build();



            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                System.err.println("Reddit API returned " + response.statusCode());
                return null;
            }

            // Parse JSON
            JsonArray listing = JsonParser.parseString(response.body()).getAsJsonArray();
            JsonObject postData = listing.get(0)
                    .getAsJsonObject()
                    .getAsJsonObject("data")
                    .getAsJsonArray("children")
                    .get(0)
                    .getAsJsonObject()
                    .getAsJsonObject("data");

            return postData;
        } catch (Exception e) {
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
            return null;
        }
    }

    private String getMedia (JsonObject postData){
        try {
            String dest = null;
            if (postData.has("is_video") && postData.get("is_video").getAsBoolean()) {
                // Why think hard when other done trick?
                dest = "https://rxddit.com" + postData.get("permalink").getAsString();
                return dest;
            }

            // Try the common image fields
            dest = postData.has("url_overridden_by_dest")
                    ? postData.get("url_overridden_by_dest").getAsString()
                    : postData.get("url").getAsString();
            if (dest != null) {
                if (dest.endsWith(".gifv")) {
                    dest = dest.substring(0, dest.length() - 1);
                } else if (dest.endsWith(".gif")) {
                    dest = dest.substring(0, dest.length() - 3) + "mp4";
                }
                return dest;
            } else {
                return null;
            }

        } catch (Exception ex) {
            //noinspection CallToPrintStackTrace
            ex.printStackTrace();
            return null;
        }
    }

    private String getRedditBearer(HttpClient client) throws IOException, InterruptedException {
        String clientId = System.getenv("REDDIT_CLIENT_ID");
        String clientSecret = System.getenv("REDDIT_CLIENT_SECRET");
        String username = System.getenv("REDDIT_USERNAME");
        String password = System.getenv("REDDIT_PASSWORD");

        String creds = Base64.getEncoder()
                .encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));

        Map<String,String> form = Map.of(
                "grant_type", "password",
                "username",   username,
                "password",   password
        );

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://www.reddit.com/api/v1/access_token"))
                .header("User-Agent", "java:org.passtoast.discordbot:v1.0")
                .header("Authorization", "Basic " + creds)
                .POST(formDataPublisher(form))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        JsonObject obj = JsonParser.parseString(resp.body()).getAsJsonObject();
        return obj.get("access_token").getAsString();
    }

    public static HttpRequest.BodyPublisher formDataPublisher(Map<String,String> data) {
        var builder = new StringBuilder();
        for (var entry : data.entrySet()) {
            if (builder.length() > 0) {
                builder.append("&");
            }
            builder.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                    .append("=")
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return HttpRequest.BodyPublishers.ofString(builder.toString());
    }
}
