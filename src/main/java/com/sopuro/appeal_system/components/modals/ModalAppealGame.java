package com.sopuro.appeal_system.components.modals;

import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.TextInput;
import discord4j.core.spec.InteractionPresentModalSpec;

import java.util.List;

public class ModalAppealGame {
    public static final String IN_GAME_MODAL = "crossroads:in-game_modal_";
    public static final String IN_GAME_MODAL_PUNISHMENT_REASON = "crossroads:in-game_modal_punishment_reason";
    public static final String IN_GAME_MODAL_APPEAL_REASON = "crossroads:in-game_modal_appeal_reason";
    public static final String IN_GAME_MODAL_APPEAL_VIDEO = "crossroads:in-game_modal_appeal_video";
    private static final String TITLE = "In-Game Appeal";
    private static final String PUNISHMENT_REASON_LABEL = "Reason for punishment";
    private static final String PUNISHMENT_REASON_PLACEHOLDER = "What you were punished for";
    private static final String APPEAL_REASON_LABEL = "Reason for appeal";
    private static final String APPEAL_REASON_PLACEHOLDER = "Why do you think the punishment should be lifted? What has changed? What have you learned?";
    private static final String APPEAL_VIDEO_LABEL = "Video Link";
    private static final String APPEAL_VIDEO_PLACEHOLDER = "Link to the video you recorded as instructed in the document.";

    public static InteractionPresentModalSpec createModal() {
        return InteractionPresentModalSpec.builder()
                .customId(IN_GAME_MODAL)
                .title(TITLE)
                .addAllComponents(List.of(
                        ActionRow.of(
                                TextInput.small(IN_GAME_MODAL_PUNISHMENT_REASON, PUNISHMENT_REASON_LABEL)
                                        .required(true)
                                        .placeholder(PUNISHMENT_REASON_PLACEHOLDER)),
                        ActionRow.of(
                                TextInput.paragraph(IN_GAME_MODAL_APPEAL_REASON, APPEAL_REASON_LABEL)
                                        .required(true)
                                        .placeholder(APPEAL_REASON_PLACEHOLDER)),
                        ActionRow.of(
                                TextInput.small(IN_GAME_MODAL_APPEAL_VIDEO, APPEAL_VIDEO_LABEL)
                                        .required(true)
                                        .placeholder(APPEAL_VIDEO_PLACEHOLDER))
                ))
                .build();
    }
}
