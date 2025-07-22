package com.sopuro.appeal_system.listeners;

import com.sopuro.appeal_system.configs.AppealSystemConfig;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.guild.GuildCreateEvent;
import discord4j.core.object.entity.Guild;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

@Service
@Slf4j
@RequiredArgsConstructor
public class GuildListListener {
    private static final Duration LEAVE_GUILD_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_RETRY_ATTEMPTS = 3;

    private final AppealSystemConfig appealSystemConfig;
    private final GatewayDiscordClient gatewayDiscordClient;

    @PostConstruct
    public void initializeEventHandlers() {
        gatewayDiscordClient
                .on(GuildCreateEvent.class)
                .flatMap(this::handleGuildCreate)
                .onErrorContinue(this::handleEventError)
                .subscribe();
    }

    private void handleEventError(Throwable error, Object event) {
        if (event instanceof GuildCreateEvent guildEvent) {
            log.error("Error processing guild create event for guild {}: {}",
                    guildEvent.getGuild().getId().asString(), error.getMessage(), error);
        } else {
            log.error("Error processing guild create event: {}", error.getMessage(), error);
        }
    }

    private Mono<Void> handleGuildCreate(GuildCreateEvent event) {
        Guild guild = event.getGuild();
        Snowflake guildId = guild.getId();
        String guildName = guild.getName();

        log.debug("Processing guild create event for guild: {} (ID: {})", guildName, guildId.asString());

        return validateAndHandleGuild(guild);
    }

    private Mono<Void> validateAndHandleGuild(Guild guild) {
        Snowflake guildId = guild.getId();
        String guildIdString = guildId.asString();
        String guildName = guild.getName();

        if (isGuildRegistered(guildIdString)) {
            log.info("Guild '{}' (ID: {}) is registered in the appeal system", guildName, guildIdString);
            return Mono.empty();
        }

        log.warn("Guild '{}' (ID: {}) is not configured in the appeal system. Attempting to leave...",
                guildName, guildIdString);

        return leaveUnregisteredGuild(guild);
    }

    private boolean isGuildRegistered(String guildId) {
        return appealSystemConfig.getAllRegisteredServerIds().contains(guildId);
    }

    private Mono<Void> leaveUnregisteredGuild(Guild guild) {
        String guildId = guild.getId().asString();
        String guildName = guild.getName();

        return guild.leave()
                .timeout(LEAVE_GUILD_TIMEOUT)
                .retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, Duration.ofSeconds(1))
                        .filter(this::isRetryableException)
                        .doBeforeRetry(retrySignal ->
                                log.debug("Retrying leave guild '{}' (ID: {}), attempt {}",
                                        guildName, guildId, retrySignal.totalRetries() + 1)))
                .doOnSuccess(ignored ->
                        log.info("Successfully left unregistered guild '{}' (ID: {})", guildName, guildId))
                .doOnError(error ->
                        log.error("Failed to leave unregistered guild '{}' (ID: {}) after {} attempts: {}",
                                guildName, guildId, MAX_RETRY_ATTEMPTS, error.getMessage()))
                .onErrorResume(error -> {
                    // Don't propagate the error to avoid disrupting other guild processing
                    log.warn("Continuing despite failure to leave guild '{}' (ID: {})", guildName, guildId);
                    return Mono.empty();
                })
                .then();
    }

    private boolean isRetryableException(Throwable throwable) {
        // Retry on common transient exceptions
        return throwable instanceof java.util.concurrent.TimeoutException ||
                throwable instanceof java.io.IOException ||
                (throwable.getMessage() != null &&
                        throwable.getMessage().toLowerCase().contains("timeout"));
    }
}