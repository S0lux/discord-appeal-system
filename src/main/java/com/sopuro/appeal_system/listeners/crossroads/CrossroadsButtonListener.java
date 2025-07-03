package com.sopuro.appeal_system.listeners.crossroads;

import com.sopuro.appeal_system.commands.panel.PanelCommandHandler;
import com.sopuro.appeal_system.configs.AppealSystemConfig;
import com.sopuro.appeal_system.dtos.GameConfigDto;
import com.sopuro.appeal_system.exceptions.AppealSystemException;
import com.sopuro.appeal_system.exceptions.UserIsNotDiscordBannedException;
import com.sopuro.appeal_system.listeners.crossroads.components.MenuAppealDiscord;
import com.sopuro.appeal_system.listeners.crossroads.components.ModalAppealGame;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.rest.http.client.ClientException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class CrossroadsButtonListener {
    private final AppealSystemConfig appealSystemConfig;

    public CrossroadsButtonListener(GatewayDiscordClient client, AppealSystemConfig appealSystemConfig) {
        this.appealSystemConfig = appealSystemConfig;
        client.on(ButtonInteractionEvent.class)
                .filter(event -> event.getCustomId().startsWith("crossroads:"))
                .flatMap(event -> handleCrossroadsButtonClick(event)
                        .onErrorResume(throwable -> handleCommandError(event, throwable)))
                .subscribe();
    }

    private Mono<Void> handleCrossroadsButtonClick(ButtonInteractionEvent event) {
        Snowflake guildId = event.getInteraction().getGuildId()
                .orElseThrow(() -> new IllegalStateException("Guild ID is not present in the interaction"));

        if (!appealSystemConfig.getAppealServerIds().contains(guildId.asString())) {
            log.warn("Guild with ID {} is not configured in the appeal system. Ignoring button interaction...", guildId.asString());
            return event.reply("This server is not configured for the appeal system.").withEphemeral(true);
        }

        GameConfigDto gameConfig = appealSystemConfig.getGameConfigByServerId(guildId.asString());

        // Discord appeal button handling
        if (event.getCustomId().startsWith(PanelCommandHandler.CROSSROADS_DISCORD_BTN_PREFIX))
            return sendDiscordAppealSelectMenu(event, gameConfig.normalizedName());

        // In-game appeal button handling
        else if (event.getCustomId().startsWith(PanelCommandHandler.CROSSROADS_IN_GAME_BTN_PREFIX))
            return event.presentModal(new ModalAppealGame(gameConfig.normalizedName()).createModal());

        // This is a fallback for any other button interactions
        else return Mono.empty();
    }

    private Mono<Void> sendDiscordAppealSelectMenu(ButtonInteractionEvent event, String normalizedGameName) {
        return event
                .reply(new MenuAppealDiscord(normalizedGameName).createSelectMenu())
                .then();
    }

    private Mono<Void> handleCommandError(ButtonInteractionEvent event, Throwable error) {
        if (error instanceof AppealSystemException)
            return event.reply(error.getMessage())
                    .withEphemeral(true);

        return event.reply("An error occurred while processing your command. Please try again later.")
                .withEphemeral(true);
    }
}
