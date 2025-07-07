package com.sopuro.appeal_system.listeners;

import com.sopuro.appeal_system.configs.AppealSystemConfig;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.guild.GuildCreateEvent;
import discord4j.core.object.entity.Guild;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class GuildListListener {
    private final AppealSystemConfig appealSystemConfig;
    private final GatewayDiscordClient gatewayDiscordClient;

    public GuildListListener(AppealSystemConfig appealSystemConfig, GatewayDiscordClient gatewayDiscordClient) {
        this.appealSystemConfig = appealSystemConfig;
        this.gatewayDiscordClient = gatewayDiscordClient;

        gatewayDiscordClient
                .on(
                        GuildCreateEvent.class,
                        event -> validateGuild(event.getGuild().getId()))
                .subscribe();
    }

    private Mono<Void> validateGuild(Snowflake guildId) {
        if (!appealSystemConfig.getAllRegisteredServerIds().contains(guildId.asString())) {
            log.warn("Guild with ID {} is not configured in the appeal system. Leaving guild...", guildId.asString());
            return gatewayDiscordClient
                    .getGuildById(guildId)
                    .flatMap(Guild::leave)
                    .doOnSuccess(ignored -> log.info("Left guild with ID {}", guildId.asString()));
        }

        return Mono.empty();
    }
}
