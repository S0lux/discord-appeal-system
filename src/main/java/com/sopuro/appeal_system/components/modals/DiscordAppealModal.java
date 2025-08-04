package com.sopuro.appeal_system.components.modals;

import com.sopuro.appeal_system.shared.enums.AppealPlatform;
import com.sopuro.appeal_system.shared.enums.PunishmentType;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.TextInput;
import discord4j.core.spec.InteractionPresentModalSpec;

import java.util.List;

public class DiscordAppealModal extends BaseAppealModal {
    private static final String MODAL_PREFIX = "crossroads:discord_modal_";
    private static final String TITLE = "Discord Appeal";
    private static final String PUNISHMENT_REASON_LABEL = "Reason for punishment";
    private static final String PUNISHMENT_REASON_PLACEHOLDER = "What you were punished for";
    private static final String APPEAL_REASON_LABEL = "Reason for appeal";
    private static final String APPEAL_REASON_PLACEHOLDER =
            "Why do you think the punishment should be lifted? What has changed? What have you learned?";

    public static final DiscordAppealModal INSTANCE = new DiscordAppealModal();

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

    @Override
    public InteractionPresentModalSpec createModal(PunishmentType punishmentType) {
        return InteractionPresentModalSpec.builder()
                .customId(createModalId(punishmentType))
                .title(getTitle())
                .addAllComponents(List.of(
                        ActionRow.of(TextInput.small(getPunishmentReasonFieldId(), getPunishmentReasonLabel())
                                .required(true)
                                .placeholder(getPunishmentReasonPlaceholder())),
                        ActionRow.of(TextInput.paragraph(getAppealReasonFieldId(), getAppealReasonLabel())
                                .required(true)
                                .placeholder(getAppealReasonPlaceholder()))))
                .build();
    }
}