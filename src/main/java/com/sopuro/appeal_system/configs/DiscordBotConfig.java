package com.sopuro.appeal_system.configs;

import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.gateway.intent.IntentSet;
import discord4j.rest.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DiscordBotConfig {
    @Bean
    public GatewayDiscordClient gatewayDiscordClient(@Value("${discord.bot.token}") String token) {
        return DiscordClientBuilder.create(token)
                .build()
                .gateway()
                .setEnabledIntents(IntentSet.all())
                .login()
                .block();
    }

    @Bean
    public RestClient discordRestClient(GatewayDiscordClient gatewayDiscordClient) {
        return gatewayDiscordClient.getRestClient();
    }
}
