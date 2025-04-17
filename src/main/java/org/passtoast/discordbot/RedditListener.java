package org.passtoast.discordbot;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.FileUpload;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

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

            // Fetch the JSON
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(jsonUrl))
                    .header("User-Agent", "java:org.passtoast.discordbot:v1.0")
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
//                JsonObject media = null;
//
//                if (postData.has("media") && postData.get("media").isJsonObject()) {
//                    media = postData.getAsJsonObject("media");
//                } else if (postData.has("secure_media") && postData.get("secure_media").isJsonObject()) {
//                    media = postData.getAsJsonObject("secure_media");
//                }
//
//                if (media != null && media.has("reddit_video")) {
//                    return media.getAsJsonObject("reddit_video").getAsString();
//                } else {
//                    return null;
//                }
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

    private void handleVideo(MessageReceivedEvent event, String fullUrl) {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        long size = getContentLength(fullUrl,client);
        if (size > 0 && size <= MAX_UPLOAD) {
            try {
                HttpResponse<byte[]> videoResp = client.send(
                        HttpRequest.newBuilder()
                                .uri(URI.create(fullUrl))
                                .header("User-Agent", "java:org.passtoast.discordbot:v1.0")
                                .build(),
                        HttpResponse.BodyHandlers.ofByteArray()
                );
                InputStream in = new ByteArrayInputStream(videoResp.body());
                event.getChannel().sendMessage("Uploading video...").queue();
                event.getChannel()
                        .sendFiles(FileUpload.fromData(in, "reddit_video.mp4"))
                        .queue(
                                null,
                                error->{
                                    System.err.println("Upload failed:");
                                    //noinspection CallToPrintStackTrace
                                    error.printStackTrace();
                                }
                        );
                return;
            } catch (Exception ex) {
                //noinspection CallToPrintStackTrace
                ex.printStackTrace();
            }
        }else {
            event.getChannel().sendMessage("Full video too big (" + size + " bytes)").queue();
        }
    }

    private long getContentLength(String url, HttpClient client) {
        try {
            HttpRequest head = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "java:org.passtoast.discordbot:v1.0")
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<Void> resp = client.send(head, HttpResponse.BodyHandlers.discarding());
            return resp.headers()
                    .firstValue("Content-Length")
                    .map(Long::parseLong)
                    .orElse(-1L);
        } catch (Exception ex) {
            //noinspection CallToPrintStackTrace
            ex.printStackTrace();
            return -1L;
        }
    }
}
