package com.sopuro.appeal_system.listeners.crossroads;

import com.sopuro.appeal_system.commands.panel.PanelCommandHandler;
import com.sopuro.appeal_system.components.menus.MenuAppealDiscord;
import com.sopuro.appeal_system.components.modals.ModalAppealGame;
import com.sopuro.appeal_system.configs.AppealSystemConfig;
import com.sopuro.appeal_system.dtos.GameConfigDto;
import com.sopuro.appeal_system.entities.GuildConfigEntity;
import com.sopuro.appeal_system.exceptions.AppealSystemException;
import com.sopuro.appeal_system.exceptions.appeal.AppealDisabledException;
import com.sopuro.appeal_system.exceptions.appeal.MissingGuildContextException;
import com.sopuro.appeal_system.repositories.GuildConfigRepository;
import com.sopuro.appeal_system.shared.enums.GuildConfig;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Optional;

@Component
@Slf4j
public class CrossroadsButtonListener {
    private final AppealSystemConfig appealSystemConfig;
    private final GuildConfigRepository guildConfigRepository;

    public CrossroadsButtonListener(
            GatewayDiscordClient client,
            AppealSystemConfig appealSystemConfig,
            GuildConfigRepository guildConfigRepository) {
        this.appealSystemConfig = appealSystemConfig;
        this.guildConfigRepository = guildConfigRepository;

        client.on(ButtonInteractionEvent.class)
                .filter(event -> event.getCustomId().startsWith("crossroads:"))
                .flatMap(event -> handleCrossroadsButtonClick(event)
                        .onErrorResume(throwable -> handleCommandError(event, throwable)))
                .subscribe();
    }

    private Mono<Void> handleCrossroadsButtonClick(ButtonInteractionEvent event) {
        return Mono.defer(() -> event.getInteraction()
                        .getGuildId()
                        .map(Mono::just)
                        .orElse(Mono.error(new MissingGuildContextException())))
                .flatMap(guildId -> {
                    if (!appealSystemConfig.getAppealServerIds().contains(guildId.asString())) {
                        log.warn(
                                "Guild with ID {} is not configured in the appeal system. Ignoring button interaction...",
                                guildId.asString());
                        return event.reply("This server is not configured for the appeal system.")
                                .withEphemeral(true)
                                .then();
                    }

                    GameConfigDto gameConfig = appealSystemConfig.getGameConfigByServerId(guildId.asString());

                    return checkAppealEnabled(gameConfig.appealServerId()).then(Mono.defer(() -> {
                        String customId = event.getCustomId();
                        // Discord appeal button handling
                        if (customId.startsWith(PanelCommandHandler.CROSSROADS_DISCORD_BTN_PREFIX)) {
                            return sendDiscordAppealSelectMenu(event, gameConfig.normalizedName());
                        }
                        // In-game appeal button handling
                        else if (customId.startsWith(PanelCommandHandler.CROSSROADS_IN_GAME_BTN_PREFIX)) {
                            return event.presentModal(new ModalAppealGame(gameConfig.normalizedName()).createModal());
                        }
                        // Unknown button handling
                        else {
                            return Mono.empty();
                        }
                    }));
                });
    }

    private Mono<Void> checkAppealEnabled(String appealServerId) {
        Mono<Optional<GuildConfigEntity>> guildConfigMono = Mono.fromCallable(() ->
                        guildConfigRepository.findByGuildIdAndConfigKey(appealServerId, GuildConfig.APPEAL_ENABLED))
                .subscribeOn(Schedulers.boundedElastic());

        return guildConfigMono.flatMap(optional -> {
            if (optional.isEmpty() || Boolean.parseBoolean(optional.get().getConfigValue())) {
                return Mono.empty();
            }
            return Mono.error(new AppealDisabledException());
        });
    }

    private Mono<Void> sendDiscordAppealSelectMenu(ButtonInteractionEvent event, String normalizedGameName) {
        log.info("Sending Discord appeal select menu for game: {}", normalizedGameName);
        return event.reply(new MenuAppealDiscord(normalizedGameName).createSelectMenu())
                .then();
    }

    private Mono<Void> handleCommandError(ButtonInteractionEvent event, Throwable error) {
        if (error instanceof AppealSystemException)
            return event.reply(error.getMessage()).withEphemeral(true);

        log.error("Error handling button interaction: {}", error.getMessage(), error);

        return event.reply("An error occurred while processing your command. Please try again later.")
                .withEphemeral(true)
                .then();
    }
}
