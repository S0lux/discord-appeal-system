package com.sopuro.appeal_system.listeners.crossroads;

import com.sopuro.appeal_system.clients.opencloud.OpenCloudClient;
import com.sopuro.appeal_system.clients.rover.RoverClient;
import com.sopuro.appeal_system.clients.rover.dtos.DiscordToRobloxDto;
import com.sopuro.appeal_system.commands.panel.PanelCommandHandler;
import com.sopuro.appeal_system.components.menus.MenuAppealDiscord;
import com.sopuro.appeal_system.components.messages.GenericErrorMessageEmbed;
import com.sopuro.appeal_system.components.modals.GameAppealModal;
import com.sopuro.appeal_system.configs.AppealSystemConfig;
import com.sopuro.appeal_system.dtos.GameConfigDto;
import com.sopuro.appeal_system.entities.GuildConfigEntity;
import com.sopuro.appeal_system.exceptions.AppealException;
import com.sopuro.appeal_system.exceptions.appeal.AppealDisabledException;
import com.sopuro.appeal_system.exceptions.appeal.MissingGuildContextException;
import com.sopuro.appeal_system.exceptions.appeal.UserIsNotRobloxBannedException;
import com.sopuro.appeal_system.repositories.GuildConfigRepository;
import com.sopuro.appeal_system.shared.enums.GuildConfig;
import com.sopuro.appeal_system.shared.enums.ServerType;
import com.sopuro.appeal_system.shared.utils.TokenHelper;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.object.entity.User;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Optional;

@Component
@Slf4j
@RequiredArgsConstructor
public class CrossroadsButtonListener {
    private static final String CROSSROADS_CUSTOM_ID_PREFIX = "crossroads:";
    private static final String GENERIC_ERROR_MESSAGE =
            "An error occurred while processing your request. Please try again later.";
    private static final String SERVER_NOT_CONFIGURED_MESSAGE = "This server is not configured for the appeal system.";

    private final GatewayDiscordClient gatewayDiscordClient;
    private final AppealSystemConfig appealSystemConfig;
    private final GuildConfigRepository guildConfigRepository;
    private final RoverClient roverClient;
    private final OpenCloudClient openCloudClient;

    @PostConstruct
    public void initializeEventHandlers() {
        gatewayDiscordClient
                .on(ButtonInteractionEvent.class)
                .filter(this::isCrossroadsButton)
                .flatMap(this::handleButtonInteraction)
                .onErrorContinue(this::handleEventError)
                .subscribe();
    }

    private void handleEventError(Throwable error, Object event) {
        if (event instanceof ButtonInteractionEvent buttonEvent) {
            log.error(
                    "Error processing button interaction event for custom ID '{}': {}",
                    buttonEvent.getCustomId(),
                    error.getMessage(),
                    error);
        } else {
            log.error("Error processing button interaction event: {}", error.getMessage(), error);
        }
    }

    private boolean isCrossroadsButton(ButtonInteractionEvent event) {
        return event.getCustomId().startsWith(CROSSROADS_CUSTOM_ID_PREFIX);
    }

    private Mono<Void> handleButtonInteraction(ButtonInteractionEvent event) {
        return validateGuildContext(event)
                .flatMap(guildId -> processButtonClick(event, guildId))
                .onErrorResume(error -> handleInteractionError(event, error));
    }

    private Mono<Snowflake> validateGuildContext(ButtonInteractionEvent event) {
        return event.getInteraction()
                .getGuildId()
                .map(Mono::just)
                .orElse(Mono.error(new MissingGuildContextException()));
    }

    private Mono<Void> processButtonClick(ButtonInteractionEvent event, Snowflake guildId) {
        String guildIdString = guildId.asString();

        if (!isGuildConfigured(guildIdString)) {
            log.warn(
                    "Guild {} is not configured for appeals. Rejecting button interaction from user {}",
                    guildIdString,
                    getUserInfo(event));
            return replyWithError(event, SERVER_NOT_CONFIGURED_MESSAGE);
        }

        GameConfigDto gameConfig = appealSystemConfig.getGameConfigByServerId(guildIdString);

        return validateAppealEnabled(gameConfig.appealServerId()).then(routeButtonAction(event, gameConfig));
    }

    private boolean isGuildConfigured(String guildId) {
        return appealSystemConfig.getAppealServerIds().contains(guildId);
    }

    private String getUserInfo(ButtonInteractionEvent event) {
        User user = event.getInteraction().getUser();
        return String.format("%s (%s)", user.getUsername(), user.getId().asString());
    }

    private Mono<Void> validateAppealEnabled(String appealServerId) {
        return Mono.fromCallable(() ->
                        guildConfigRepository.findByGuildIdAndConfigKey(appealServerId, GuildConfig.APPEAL_ENABLED))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(this::checkAppealEnabledConfig);
    }

    private Mono<Void> checkAppealEnabledConfig(Optional<GuildConfigEntity> configOpt) {
        if (configOpt.isEmpty()) {
            // Appeals enabled by default if config doesn't exist
            return Mono.empty();
        }

        boolean isEnabled = Boolean.parseBoolean(configOpt.get().getConfigValue());
        return isEnabled ? Mono.empty() : Mono.error(new AppealDisabledException());
    }

    private Mono<Void> routeButtonAction(ButtonInteractionEvent event, GameConfigDto gameConfig) {
        String customId = event.getCustomId();

        if (customId.startsWith(PanelCommandHandler.CROSSROADS_DISCORD_BTN_PREFIX)) {
            return handleDiscordAppealButton(event);
        } else if (customId.startsWith(PanelCommandHandler.CROSSROADS_IN_GAME_BTN_PREFIX)) {
            return handleInGameAppealButton(event);
        } else {
            log.warn("Unknown crossroads button custom ID: {} from user {}", customId, getUserInfo(event));
            return Mono.empty();
        }
    }

    private Mono<Void> handleDiscordAppealButton(ButtonInteractionEvent event) {
        String userInfo = getUserInfo(event);
        String guildId =
                event.getInteraction().getGuildId().map(Snowflake::asString).orElse("Unknown");
        String normalizedGameName =
                appealSystemConfig.getGameConfigByServerId(guildId).normalizedName();

        log.info(
                "Processing Discord appeal request for user {} in game '{}' (guild: {})",
                userInfo,
                normalizedGameName,
                guildId);

        return event.reply(MenuAppealDiscord.createSelectMenu())
                .doOnSuccess(ignored -> log.debug("Successfully sent Discord appeal menu to user {}", userInfo))
                .then();
    }

    private Mono<Void> handleInGameAppealButton(ButtonInteractionEvent event) {
        String userInfo = getUserInfo(event);
        String guildId =
                event.getInteraction().getGuildId().map(Snowflake::asString).orElse("Unknown");
        GameConfigDto gameConfig = appealSystemConfig.getGameConfigByServerId(guildId);

        log.info(
                "Processing in-game appeal request for user {} in game '{}' (from guild: {})",
                userInfo,
                gameConfig.normalizedName(),
                guildId);

        return event.presentModal(GameAppealModal.INSTANCE.createModal());
    }

    private Mono<Void> handleInteractionError(ButtonInteractionEvent event, Throwable error) {
        String userInfo = getUserInfo(event);

        if (error instanceof AppealException appealException)
            return replyWithError(event, appealException.getMessage());

        log.error(
                "Unexpected error processing button interaction for user {}: {}", userInfo, error.getMessage(), error);
        return replyWithError(event, GENERIC_ERROR_MESSAGE);
    }

    private Mono<Void> replyWithError(ButtonInteractionEvent event, String message) {
        return event.editReply(GenericErrorMessageEmbed.create(message))
                .onErrorResume(replyError -> {
                    log.error("Failed to send error reply to user {}: {}", getUserInfo(event), replyError.getMessage());
                    return Mono.empty();
                })
                .then();
    }
}