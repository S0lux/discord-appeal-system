package com.sopuro.appeal_system.commands.panel.crossroads;

import com.sopuro.appeal_system.commands.panel.PanelCommandHandler;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.TextInput;
import discord4j.core.spec.InteractionPresentModalSpec;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Component
@Slf4j
public class CrossroadsModalHandler {
    private final GatewayDiscordClient gatewayDiscordClient;

    public static final String CROSSROADS_DISCORD_MODAL = "crossroads:discord_modal";
    public static final String CROSSROADS_DISCORD_MODAL_USER_ID = "crossroads:discord_modal_user_id";
    public static final String CROSSROADS_DISCORD_MODAL_PUNISHMENT = "crossroads:discord_modal_punishment";
    public static final String CROSSROADS_DISCORD_MODAL_PUNISHMENT_REASON = "crossroads:discord_modal_punishment_reason";
    public static final String CROSSROADS_DISCORD_MODAL_APPEAL_REASON = "crossroads:discord_modal_appeal_reason";

    public static final String CROSSROADS_IN_GAME_MODAL = "crossroads:in-game_modal";
    public static final String CROSSROADS_IN_GAME_MODAL_ROBLOX_ID = "crossroads:in-game_modal_roblox_id";
    public static final String CROSSROADS_IN_GAME_MODAL_PUNISHMENT = "crossroads:in-game_modal_punishment";
    public static final String CROSSROADS_IN_GAME_MODAL_PUNISHMENT_REASON = "crossroads:in-game_modal_punishment_reason";
    public static final String CROSSROADS_IN_GAME_MODAL_APPEAL_REASON = "crossroads:in-game_modal_appeal_reason";

    public CrossroadsModalHandler(GatewayDiscordClient client) {
        this.gatewayDiscordClient = client;
        gatewayDiscordClient.on(ButtonInteractionEvent.class)
                .filter(event -> event.getCustomId().startsWith("crossroads:"))
                .flatMap(this::handleCrossroadsButtonClick)
                .subscribe();
    }

    private Mono<Void> handleCrossroadsButtonClick(ButtonInteractionEvent event) {
        return switch (event.getCustomId()) {
            case PanelCommandHandler.CROSSROADS_DISCORD_BTN -> event.presentModal(createDiscordAppealModal());
            case PanelCommandHandler.CROSSROADS_IN_GAME_BTN -> event.presentModal(createInGameAppealModal());
            default -> Mono.empty();
        };

    }

    private InteractionPresentModalSpec createDiscordAppealModal() {
        return InteractionPresentModalSpec.builder()
                .customId(CROSSROADS_DISCORD_MODAL)
                .title("Discord Appeal")
                .addAllComponents(List.of(
                        ActionRow.of(
                                TextInput.small(CROSSROADS_DISCORD_MODAL_USER_ID, "Discord user ID")
                                        .required(true)
                                        .placeholder("The ID of the Discord account you are appealing for/as")),
                        ActionRow.of(
                                TextInput.small(CROSSROADS_DISCORD_MODAL_PUNISHMENT, "Punishment type")
                                        .required(true)
                                        .placeholder("ban, mute, etc")),
                        ActionRow.of(
                                TextInput.small(CROSSROADS_DISCORD_MODAL_PUNISHMENT_REASON, "Reason for punishment")
                                        .required(true)
                                        .placeholder("What you were punished for")),
                        ActionRow.of(
                                TextInput.paragraph(CROSSROADS_DISCORD_MODAL_APPEAL_REASON, "Reason for appeal")
                                        .required(true)
                                        .placeholder("Why do you think the punishment should be lifted? What has changed? What have you learned?"))
                ))
                .build();
    }

    private InteractionPresentModalSpec createInGameAppealModal() {
        return InteractionPresentModalSpec.builder()
                .customId(CROSSROADS_IN_GAME_MODAL)
                .title("In-Game Appeal")
                .addAllComponents(List.of(
                        ActionRow.of(
                                TextInput.small(CROSSROADS_IN_GAME_MODAL_ROBLOX_ID, "Roblox user ID")
                                        .required(true)
                                        .placeholder("The ID of the Roblox account you are appealing for/as")),
                        ActionRow.of(
                                TextInput.small(CROSSROADS_IN_GAME_MODAL_PUNISHMENT, "Punishment type")
                                        .required(true)
                                        .placeholder("Only permanent bans can be appealed")),
                        ActionRow.of(
                                TextInput.small(CROSSROADS_IN_GAME_MODAL_PUNISHMENT_REASON, "Reason for punishment")
                                        .required(true)
                                        .placeholder("What you were punished for")),
                        ActionRow.of(
                                TextInput.paragraph(CROSSROADS_IN_GAME_MODAL_APPEAL_REASON, "Reason for appeal")
                                        .required(true)
                                        .placeholder("Why do you think the punishment should be lifted? What has changed? What have you learned?"))
                ))
                .build();
    }
}
