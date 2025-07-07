package com.sopuro.appeal_system.listeners.crossroads.components;

import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.TextInput;
import discord4j.core.spec.InteractionPresentModalSpec;

import java.util.List;

public record ModalAppealDiscord(String normalizedGameName) {
    public static final String DISCORD_MODAL_PREFIX = "crossroads:discord_modal_";
    public static final String DISCORD_MODAL_PUNISHMENT_REASON = "crossroads:discord_modal_punishment_reason";
    public static final String DISCORD_MODAL_APPEAL_REASON = "crossroads:discord_modal_appeal_reason";
    private static final String TITLE = "Discord Appeal";
    private static final String PUNISHMENT_REASON_LABEL = "Reason for punishment";
    private static final String PUNISHMENT_REASON_PLACEHOLDER = "What you were punished for";
    private static final String APPEAL_REASON_LABEL = "Reason for appeal";
    private static final String APPEAL_REASON_PLACEHOLDER = "Why do you think the punishment should be lifted? What has changed? What have you learned?";

    public InteractionPresentModalSpec createModal() {
        return InteractionPresentModalSpec.builder()
                .customId(DISCORD_MODAL_PREFIX + normalizedGameName)
                .title(TITLE)
                .addAllComponents(List.of(
                        ActionRow.of(
                                TextInput.small(DISCORD_MODAL_PUNISHMENT_REASON, PUNISHMENT_REASON_LABEL)
                                        .required(true)
                                        .placeholder(PUNISHMENT_REASON_PLACEHOLDER)),
                        ActionRow.of(
                                TextInput.paragraph(DISCORD_MODAL_APPEAL_REASON, APPEAL_REASON_LABEL)
                                        .required(true)
                                        .placeholder(APPEAL_REASON_PLACEHOLDER))
                ))
                .build();
    }
}