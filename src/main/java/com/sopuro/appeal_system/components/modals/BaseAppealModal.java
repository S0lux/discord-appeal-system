package com.sopuro.appeal_system.components.modals;

import com.sopuro.appeal_system.shared.enums.PunishmentType;
import discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.TextInput;
import discord4j.core.spec.InteractionPresentModalSpec;

import java.util.List;

public abstract class BaseAppealModal {
    protected static final String PUNISHMENT_REASON_SUFFIX = "_punishment_reason";
    protected static final String APPEAL_REASON_SUFFIX = "_appeal_reason";
    protected static final String DEFAULT_NO_REASON = "No reason provided";

    // Abstract methods that subclasses must implement
    public abstract String getModalPrefix();
    public abstract String getTitle();
    public abstract String getPunishmentReasonLabel();
    public abstract String getPunishmentReasonPlaceholder();
    public abstract String getAppealReasonLabel();
    public abstract String getAppealReasonPlaceholder();

    protected String createModalId(PunishmentType punishmentType) {
        return getModalPrefix() + punishmentType.name();
    }

    protected String createModalId() {
        return getModalPrefix();
    }

    protected String getPunishmentReasonFieldId() {
        return getModalPrefix().replaceAll("_$", "") + PUNISHMENT_REASON_SUFFIX;
    }

    protected String getAppealReasonFieldId() {
        return getModalPrefix().replaceAll("_$", "") + APPEAL_REASON_SUFFIX;
    }

    public PunishmentType getPunishmentTypeFromModalId(String modalId) {
        if (modalId == null || !modalId.startsWith(getModalPrefix())) {
            throw new IllegalArgumentException("Invalid modal ID: " + modalId);
        }

        String suffix = modalId.substring(getModalPrefix().length());
        if (suffix.isEmpty()) {
            throw new IllegalArgumentException("Modal ID does not contain punishment type: " + modalId);
        }

        String[] parts = suffix.split("_");
        return PunishmentType.valueOf(parts[0]);
    }

    public String getPunishmentReason(ModalSubmitInteractionEvent event) {
        return extractFieldValue(event, getPunishmentReasonFieldId());
    }

    public String getAppealReason(ModalSubmitInteractionEvent event) {
        return extractFieldValue(event, getAppealReasonFieldId());
    }

    private String extractFieldValue(ModalSubmitInteractionEvent event, String fieldId) {
        return event.getComponents(TextInput.class)
                .stream()
                .filter(input -> input.getCustomId().equals(fieldId))
                .findFirst()
                .map(input -> input.getValue().orElse(DEFAULT_NO_REASON))
                .orElse(DEFAULT_NO_REASON);
    }

    public abstract InteractionPresentModalSpec createModal(PunishmentType punishmentType);

    public InteractionPresentModalSpec createModal() {
        throw new UnsupportedOperationException("This modal type requires a punishment type parameter");
    }

    protected List<ActionRow> createModalComponents() {
        return List.of(
                ActionRow.of(TextInput.small(getPunishmentReasonFieldId(), getPunishmentReasonLabel())
                        .required(true)
                        .placeholder(getPunishmentReasonPlaceholder())),
                ActionRow.of(TextInput.paragraph(getAppealReasonFieldId(), getAppealReasonLabel())
                        .required(true)
                        .placeholder(getAppealReasonPlaceholder()))
        );
    }

    protected TextInput createPunishmentReasonField() {
        return TextInput.small(getPunishmentReasonFieldId(), getPunishmentReasonLabel())
                .required(true)
                .placeholder(getPunishmentReasonPlaceholder());
    }

    protected TextInput createAppealReasonField() {
        return TextInput.paragraph(getAppealReasonFieldId(), getAppealReasonLabel())
                .required(true)
                .placeholder(getAppealReasonPlaceholder());
    }
}