package com.sopuro.appeal_system.listeners.crossroads;

import com.sopuro.appeal_system.configs.AppealSystemConfig;
import com.sopuro.appeal_system.exceptions.AppealSystemException;
import com.sopuro.appeal_system.exceptions.NotAppealGuildException;
import com.sopuro.appeal_system.exceptions.UserIsNotDiscordBannedException;
import com.sopuro.appeal_system.listeners.crossroads.components.MenuAppealDiscord;
import com.sopuro.appeal_system.listeners.crossroads.components.ModalAppealDiscord;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;
import discord4j.rest.http.client.ClientException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class CrossroadsMenuListener {
    private final GatewayDiscordClient gatewayDiscordClient;
    private final AppealSystemConfig appealSystemConfig;

    public CrossroadsMenuListener(GatewayDiscordClient gatewayDiscordClient, AppealSystemConfig appealSystemConfig) {
        this.gatewayDiscordClient = gatewayDiscordClient;
        this.appealSystemConfig = appealSystemConfig;

        gatewayDiscordClient.on(SelectMenuInteractionEvent.class)
                .filter(menu -> menu.getCustomId().startsWith(MenuAppealDiscord.DISCORD_SELECT_MENU_PREFIX))
                .flatMap(event -> handleMenuSelect(event)
                        .onErrorResume(throwable -> handleCommandError(event, throwable)))
                .subscribe();
    }

    private Mono<Void> ensureUserIsDiscordBanned(SelectMenuInteractionEvent event, String communityServerId) {
        return gatewayDiscordClient
                .getGuildById(Snowflake.of(communityServerId))
                .flatMap(guild -> guild.getBan(event.getUser().getId()))
                .flatMap(ignored -> Mono.empty())
                .onErrorResume(ClientException.class, ex -> {
                    if (ex.getStatus().code() == 404) {
                        return Mono.error(new UserIsNotDiscordBannedException());
                    }
                    return Mono.error(ex);
                }).then();
    }

    private Mono<Void> handleMenuSelect(SelectMenuInteractionEvent event) {
        Snowflake guildId = event.getInteraction().getGuildId()
                .orElseThrow(() -> new IllegalStateException("Guild ID is not present in the interaction"));

        if (!appealSystemConfig.getAppealServerIds().contains(guildId.asString()))
            return Mono.error(new NotAppealGuildException(guildId.asString()));

        if (event.getValues().isEmpty()) {
            return event.reply("No option selected. Please try again.").withEphemeral(true);
        }

        String selectedOption = event.getValues().getFirst();
        String normalizedGameName = appealSystemConfig.getGameConfigByServerId(guildId.asString()).normalizedName();

        if (MenuAppealDiscord.BAN_VALUE.equals(selectedOption)) {
            return ensureUserIsDiscordBanned(event, event.getInteraction().getGuildId().orElseThrow().asString())
                    .then(event.presentModal(new ModalAppealDiscord(normalizedGameName).createModal()));
        } else if (MenuAppealDiscord.WARNING_VALUE.equals(selectedOption)) {
            return event.presentModal(new ModalAppealDiscord(normalizedGameName).createModal());
        } else {
            return event.reply("Invalid selection. Please try again.").withEphemeral(true);
        }
    }

    private Mono<Void> handleCommandError(SelectMenuInteractionEvent event, Throwable error) {
        if (error instanceof AppealSystemException)
            return event.reply(error.getMessage())
                    .withEphemeral(true);

        return event.reply("An error occurred while processing your command. Please try again later.")
                .withEphemeral(true);
    }
}
