package com.sopuro.appeal_system.listeners;

import com.sopuro.appeal_system.commands.panel.PanelCommandHandler;
import com.sopuro.appeal_system.configs.AppealSystemConfig;
import com.sopuro.appeal_system.dtos.GameConfigDto;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.TextInput;
import discord4j.core.spec.InteractionPresentModalSpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@Slf4j
public class CrossroadsInputListener {
    private final AppealSystemConfig appealSystemConfig;

    public static final String DISCORD_MODAL_PREFIX = "crossroads:discord_modal_";
    public static final String DISCORD_MODAL_PUNISHMENT = "crossroads:discord_modal_punishment";
    public static final String DISCORD_MODAL_PUNISHMENT_REASON = "crossroads:discord_modal_punishment_reason";
    public static final String DISCORD_MODAL_APPEAL_REASON = "crossroads:discord_modal_appeal_reason";

    public static final String IN_GAME_MODAL_PREFIX = "crossroads:in-game_modal_";
    public static final String IN_GAME_MODAL_PUNISHMENT = "crossroads:in-game_modal_punishment";
    public static final String IN_GAME_MODAL_PUNISHMENT_REASON = "crossroads:in-game_modal_punishment_reason";
    public static final String IN_GAME_MODAL_APPEAL_REASON = "crossroads:in-game_modal_appeal_reason";

    public CrossroadsInputListener(GatewayDiscordClient client, AppealSystemConfig appealSystemConfig) {
        this.appealSystemConfig = appealSystemConfig;
        client.on(ButtonInteractionEvent.class)
                .filter(event -> event.getCustomId().startsWith("crossroads:"))
                .flatMap(this::handleCrossroadsButtonClick)
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

        if (event.getCustomId().startsWith(PanelCommandHandler.CROSSROADS_DISCORD_BTN_PREFIX))
            return event.presentModal(createDiscordAppealModal(gameConfig.normalizedName()));
        else if (event.getCustomId().startsWith(PanelCommandHandler.CROSSROADS_IN_GAME_BTN_PREFIX))
            return event.presentModal(createInGameAppealModal(gameConfig.normalizedName()));
        else return Mono.empty();
    }

    private InteractionPresentModalSpec createDiscordAppealModal(String normalizedGameName) {
        return InteractionPresentModalSpec.builder()
                .customId(DISCORD_MODAL_PREFIX + normalizedGameName)
                .title("Discord Appeal")
                .addAllComponents(List.of(
                        ActionRow.of(
                                TextInput.small(DISCORD_MODAL_PUNISHMENT, "Punishment type")
                                        .required(true)
                                        .placeholder("ban, mute, etc")),
                        ActionRow.of(
                                TextInput.small(DISCORD_MODAL_PUNISHMENT_REASON, "Reason for punishment")
                                        .required(true)
                                        .placeholder("What you were punished for")),
                        ActionRow.of(
                                TextInput.paragraph(DISCORD_MODAL_APPEAL_REASON, "Reason for appeal")
                                        .required(true)
                                        .placeholder("Why do you think the punishment should be lifted? What has changed? What have you learned?"))
                ))
                .build();
    }

    private InteractionPresentModalSpec createInGameAppealModal(String normalizedGameName) {
        return InteractionPresentModalSpec.builder()
                .customId(IN_GAME_MODAL_PREFIX + normalizedGameName)
                .title("In-Game Appeal")
                .addAllComponents(List.of(
                        ActionRow.of(
                                TextInput.small(IN_GAME_MODAL_PUNISHMENT, "Punishment type")
                                        .required(true)
                                        .placeholder("Only permanent bans can be appealed")),
                        ActionRow.of(
                                TextInput.small(IN_GAME_MODAL_PUNISHMENT_REASON, "Reason for punishment")
                                        .required(true)
                                        .placeholder("What you were punished for")),
                        ActionRow.of(
                                TextInput.paragraph(IN_GAME_MODAL_APPEAL_REASON, "Reason for appeal")
                                        .required(true)
                                        .placeholder("Why do you think the punishment should be lifted? What has changed? What have you learned?"))
                ))
                .build();
    }
}
