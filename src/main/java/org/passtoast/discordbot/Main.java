package org.passtoast.discordbot;

import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;

import javax.security.auth.login.LoginException;

public class Main {
    public static void main(String[] args) {
        String token = System.getenv("DISCORD_TOKEN");
        if (token == null) {
            System.err.println("Error: Failed to retrieve Discord Token.");
            System.exit(1);
        }
        try {
        JDABuilder.createDefault(
                        token,
                        GatewayIntent.GUILD_WEBHOOKS,
                        // Always include GUILD_MESSAGES so you can see messages in guild text channels
                        GatewayIntent.GUILD_MESSAGES,
                        // Required to read the actual content of messages
                        GatewayIntent.MESSAGE_CONTENT
                )
                // Optionally tweak caching policies (not strictly needed here)
                .setMemberCachePolicy(MemberCachePolicy.NONE)
                .addEventListeners(new RedditListener())
                .build();
        }catch(LoginException ex){
            ex.printStackTrace();
        }
    }
}