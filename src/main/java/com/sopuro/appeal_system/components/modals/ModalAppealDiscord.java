package com.sopuro.appeal_system.components.modals;

import com.sopuro.appeal_system.shared.enums.PunishmentType;
import discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.TextInput;
import discord4j.core.spec.InteractionPresentModalSpec;

import java.util.List;

public class ModalAppealDiscord {
    public static final String DISCORD_MODAL_PREFIX = "crossroads:discord_modal_";
    public static final String DISCORD_MODAL_PUNISHMENT_REASON = "crossroads:discord_modal_punishment_reason";
    public static final String DISCORD_MODAL_APPEAL_REASON = "crossroads:discord_modal_appeal_reason";
    private static final String TITLE = "Discord Appeal";
    private static final String PUNISHMENT_REASON_LABEL = "Reason for punishment";
    private static final String PUNISHMENT_REASON_PLACEHOLDER = "What you were punished for";
    private static final String APPEAL_REASON_LABEL = "Reason for appeal";
    private static final String APPEAL_REASON_PLACEHOLDER =
            "Why do you think the punishment should be lifted? What has changed? What have you learned?";

    public static PunishmentType getPunishmentTypeFromModalId(String modalId) {
        if (modalId == null || !modalId.startsWith(DISCORD_MODAL_PREFIX))
            throw new IllegalArgumentException("Invalid modal ID: " + modalId);

        String[] parts = modalId.substring(DISCORD_MODAL_PREFIX.length()).split("_");
        return PunishmentType.valueOf(parts[0]);
    }

    public static String getPunishmentReason(ModalSubmitInteractionEvent event) {
        return event.getComponents(TextInput.class)
                .stream()
                .filter(input -> input.getCustomId().equals(DISCORD_MODAL_PUNISHMENT_REASON))
                .findFirst()
                .map(input -> input.getValue().orElse("No reason provided"))
                .orElse("No reason provided");
    }

    public static String getAppealReason(ModalSubmitInteractionEvent event) {
        return event.getComponents(TextInput.class)
                .stream()
                .filter(input -> input.getCustomId().equals(DISCORD_MODAL_APPEAL_REASON))
                .findFirst()
                .map(input -> input.getValue().orElse("No reason provided"))
                .orElse("No reason provided");
    }

    public static InteractionPresentModalSpec createModal(PunishmentType punishmentType) {
        return InteractionPresentModalSpec.builder()
                .customId(DISCORD_MODAL_PREFIX + punishmentType.name())
                .title(TITLE)
                .addAllComponents(List.of(
                        ActionRow.of(TextInput.small(DISCORD_MODAL_PUNISHMENT_REASON, PUNISHMENT_REASON_LABEL)
                                .required(true)
                                .placeholder(PUNISHMENT_REASON_PLACEHOLDER)),
                        ActionRow.of(TextInput.paragraph(DISCORD_MODAL_APPEAL_REASON, APPEAL_REASON_LABEL)
                                .required(true)
                                .placeholder(APPEAL_REASON_PLACEHOLDER))))
                .build();
    }
}