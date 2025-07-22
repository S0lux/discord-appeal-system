package com.sopuro.appeal_system.listeners.crossroads;

import com.sopuro.appeal_system.components.menus.MenuAppealDiscord;
import com.sopuro.appeal_system.components.modals.ModalAppealDiscord;
import com.sopuro.appeal_system.configs.AppealSystemConfig;
import com.sopuro.appeal_system.dtos.GameConfigDto;
import com.sopuro.appeal_system.exceptions.AppealException;
import com.sopuro.appeal_system.exceptions.appeal.NotAppealGuildException;
import com.sopuro.appeal_system.exceptions.appeal.UserIsNotDiscordBannedException;
import com.sopuro.appeal_system.shared.enums.PunishmentType;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;
import discord4j.core.object.entity.User;
import discord4j.rest.http.client.ClientException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class CrossroadsMenuListener {
    private static final String GENERIC_ERROR_MESSAGE = "An error occurred while processing your request. Please try again later.";
    private static final String NO_SELECTION_MESSAGE = "No option selected. Please try again.";
    private static final String INVALID_SELECTION_MESSAGE = "Invalid selection. Please try again.";

    private final GatewayDiscordClient gatewayDiscordClient;
    private final AppealSystemConfig appealSystemConfig;

    @PostConstruct
    public void initializeEventHandlers() {
        gatewayDiscordClient
                .on(SelectMenuInteractionEvent.class)
                .filter(this::isDiscordAppealMenu)
                .flatMap(this::handleMenuInteraction)
                .onErrorContinue(this::handleEventError)
                .subscribe();
    }

    private void handleEventError(Throwable error, Object event) {
        if (event instanceof SelectMenuInteractionEvent menuEvent) {
            log.error("Error processing select menu interaction event for custom ID '{}': {}",
                    menuEvent.getCustomId(), error.getMessage(), error);
        } else {
            log.error("Error processing select menu interaction event: {}", error.getMessage(), error);
        }
    }

    private boolean isDiscordAppealMenu(SelectMenuInteractionEvent event) {
        return event.getCustomId().startsWith(MenuAppealDiscord.DISCORD_SELECT_MENU_PREFIX);
    }

    private Mono<Void> handleMenuInteraction(SelectMenuInteractionEvent event) {
        return validateGuildContext(event)
                .flatMap(guildId -> processMenuSelection(event, guildId))
                .onErrorResume(error -> handleInteractionError(event, error));
    }

    private Mono<Snowflake> validateGuildContext(SelectMenuInteractionEvent event) {
        return event.getInteraction()
                .getGuildId()
                .map(Mono::just)
                .orElse(Mono.error(new IllegalStateException("Guild ID is not present in the interaction")));
    }

    private Mono<Void> processMenuSelection(SelectMenuInteractionEvent event, Snowflake guildId) {
        String guildIdString = guildId.asString();

        if (!isAppealGuild(guildIdString)) {
            return Mono.error(new NotAppealGuildException(guildIdString));
        }

        List<String> selectedValues = event.getValues();
        if (selectedValues.isEmpty()) {
            log.debug("No selection made by user {} in guild {}", getUserInfo(event), guildIdString);
            return replyWithError(event, NO_SELECTION_MESSAGE);
        }

        String selectedOption = selectedValues.get(0);
        GameConfigDto gameConfig = appealSystemConfig.getGameConfigByServerId(guildIdString);

        log.info("Processing Discord appeal menu selection '{}' for user {} in game '{}' (guild: {})",
                selectedOption, getUserInfo(event), gameConfig.normalizedName(), guildIdString);

        return routeMenuSelection(event, selectedOption, gameConfig);
    }

    private boolean isAppealGuild(String guildId) {
        return appealSystemConfig.getAppealServerIds().contains(guildId);
    }

    private String getUserInfo(SelectMenuInteractionEvent event) {
        User user = event.getUser();
        return String.format("%s (%s)", user.getUsername(), user.getId().asString());
    }

    private Mono<Void> routeMenuSelection(SelectMenuInteractionEvent event, String selectedOption, GameConfigDto gameConfig) {
        return switch (selectedOption) {
            case MenuAppealDiscord.BAN_VALUE -> handleBanAppeal(event, gameConfig);
            case MenuAppealDiscord.WARNING_VALUE -> handleWarningAppeal(event, gameConfig);
            default -> {
                log.warn("Invalid menu selection '{}' by user {} in guild {}",
                        selectedOption, getUserInfo(event),
                        event.getInteraction().getGuildId().map(Snowflake::asString).orElse("Unknown"));
                yield replyWithError(event, INVALID_SELECTION_MESSAGE);
            }
        };
    }

    private Mono<Void> handleBanAppeal(SelectMenuInteractionEvent event, GameConfigDto gameConfig) {
        String userInfo = getUserInfo(event);
        String communityServerId = gameConfig.communityServerId();

        log.debug("Processing ban appeal for user {} - verifying ban status in community server {}",
                userInfo, communityServerId);

        return validateUserBanStatus(event, communityServerId)
                .then(presentBanAppealModal(event, gameConfig))
                .doOnSuccess(ignored ->
                        log.debug("Successfully presented ban appeal modal to user {}", userInfo));
    }

    private Mono<Void> handleWarningAppeal(SelectMenuInteractionEvent event, GameConfigDto gameConfig) {
        String userInfo = getUserInfo(event);

        log.debug("Processing warning appeal for user {}", userInfo);

        return presentWarningAppealModal(event, gameConfig)
                .doOnSuccess(ignored ->
                        log.debug("Successfully presented warning appeal modal to user {}", userInfo));
    }

    private Mono<Void> validateUserBanStatus(SelectMenuInteractionEvent event, String communityServerId) {
        Snowflake userId = event.getUser().getId();
        Snowflake communityGuildId = Snowflake.of(communityServerId);

        return gatewayDiscordClient
                .getGuildById(communityGuildId)
                .flatMap(guild -> guild.getBan(userId))
                .then()
                .onErrorResume(ClientException.class, this::handleBanCheckError);
    }

    private Mono<Void> handleBanCheckError(ClientException ex) {
        if (ex.getStatus().code() == 404) {
            // User is not banned (404 = ban not found)
            return Mono.error(new UserIsNotDiscordBannedException());
        }
        // Re-throw other client exceptions
        return Mono.error(ex);
    }

    private Mono<Void> presentBanAppealModal(SelectMenuInteractionEvent event, GameConfigDto gameConfig) {
        ModalAppealDiscord modal = new ModalAppealDiscord(gameConfig.normalizedName());
        return event.presentModal(modal.createModal(PunishmentType.BAN));
    }

    private Mono<Void> presentWarningAppealModal(SelectMenuInteractionEvent event, GameConfigDto gameConfig) {
        ModalAppealDiscord modal = new ModalAppealDiscord(gameConfig.normalizedName());
        return event.presentModal(modal.createModal(PunishmentType.WARN));
    }

    private Mono<Void> handleInteractionError(SelectMenuInteractionEvent event, Throwable error) {
        String userInfo = getUserInfo(event);

        if (error instanceof AppealException appealException) {
            log.warn("Appeal exception for user {}: {}", userInfo, appealException.getMessage());
            return replyWithError(event, appealException.getMessage());
        }

        log.error("Unexpected error processing menu interaction for user {}: {}",
                userInfo, error.getMessage(), error);
        return replyWithError(event, GENERIC_ERROR_MESSAGE);
    }

    private Mono<Void> replyWithError(SelectMenuInteractionEvent event, String message) {
        return event.reply(message)
                .withEphemeral(true)
                .onErrorResume(replyError -> {
                    log.error("Failed to send error reply to user {}: {}",
                            getUserInfo(event), replyError.getMessage());
                    return Mono.empty();
                })
                .then();
    }
}