package com.sopuro.appeal_system.components.modals;

import com.sopuro.appeal_system.shared.enums.PunishmentType;
import discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.TextInput;
import discord4j.core.spec.InteractionPresentModalSpec;

import java.util.List;

public class GameAppealModal extends BaseAppealModal {
    private static final String MODAL_PREFIX = "crossroads:in-game_modal_";
    private static final String VIDEO_FIELD_SUFFIX = "_appeal_video";
    private static final String TITLE = "In-Game Appeal";
    private static final String PUNISHMENT_REASON_LABEL = "Reason for punishment";
    private static final String PUNISHMENT_REASON_PLACEHOLDER = "What you were punished for";
    private static final String APPEAL_REASON_LABEL = "Reason for appeal";
    private static final String APPEAL_REASON_PLACEHOLDER = "Why do you think the punishment should be lifted? What has changed? What have you learned?";
    private static final String APPEAL_VIDEO_LABEL = "Video Link";
    private static final String APPEAL_VIDEO_PLACEHOLDER = "Link to the video you recorded as instructed in the document.";

    public static final GameAppealModal INSTANCE = new GameAppealModal();

    @Override
    public String getModalPrefix() {
        return MODAL_PREFIX;
    }

    @Override
    public String getTitle() {
        return TITLE;
    }

    @Override
    public String getPunishmentReasonLabel() {
        return PUNISHMENT_REASON_LABEL;
    }

    @Override
    public String getPunishmentReasonPlaceholder() {
        return PUNISHMENT_REASON_PLACEHOLDER;
    }

    @Override
    public String getAppealReasonLabel() {
        return APPEAL_REASON_LABEL;
    }

    @Override
    public String getAppealReasonPlaceholder() {
        return APPEAL_REASON_PLACEHOLDER;
    }

    public String getVideoFieldId() {
        return getModalPrefix().replaceAll("_$", "") + VIDEO_FIELD_SUFFIX;
    }

    public String getVideoLink(ModalSubmitInteractionEvent event) {
        return event.getComponents(TextInput.class)
                .stream()
                .filter(input -> input.getCustomId().equals(getVideoFieldId()))
                .findFirst()
                .map(input -> input.getValue().orElse(DEFAULT_NO_REASON))
                .orElse(DEFAULT_NO_REASON);
    }

    @Override
    protected String createModalId() {
        return getModalPrefix();
    }

    @Override
    public PunishmentType getPunishmentTypeFromModalId(String modalId) {
        throw new UnsupportedOperationException("GameAppealModal does not encode punishment type in modal ID");
    }

    @Override
    public InteractionPresentModalSpec createModal(PunishmentType punishmentType) {
        return createModal();
    }

    @Override
    public InteractionPresentModalSpec createModal() {
        return InteractionPresentModalSpec.builder()
                .customId(createModalId())
                .title(getTitle())
                .addAllComponents(List.of(
                        ActionRow.of(TextInput.small(getPunishmentReasonFieldId(), getPunishmentReasonLabel())
                                .required(true)
                                .placeholder(getPunishmentReasonPlaceholder())),
                        ActionRow.of(TextInput.paragraph(getAppealReasonFieldId(), getAppealReasonLabel())
                                .required(true)
                                .placeholder(getAppealReasonPlaceholder())),
                        ActionRow.of(TextInput.small(getVideoFieldId(), APPEAL_VIDEO_LABEL)
                                .required(true)
                                .placeholder(APPEAL_VIDEO_PLACEHOLDER))))
                .build();
    }
}