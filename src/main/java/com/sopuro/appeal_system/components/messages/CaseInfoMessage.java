package com.sopuro.appeal_system.components.messages;

import com.sopuro.appeal_system.components.modals.ModalAppealDiscord;
import com.sopuro.appeal_system.shared.enums.PunishmentType;
import discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent;
import discord4j.core.object.component.Container;
import discord4j.core.object.component.Separator;
import discord4j.core.object.component.TextDisplay;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.MessageCreateSpec;

public class CaseInfoMessage {

    public static MessageCreateSpec create(ModalSubmitInteractionEvent event, String caseId) {
        String gameName = ModalAppealDiscord.getNormalizedGameNameFromModalId(event.getCustomId());
        PunishmentType punishmentType = ModalAppealDiscord.getPunishmentTypeFromModalId(event.getCustomId());

        String punishmentReason = ModalAppealDiscord.getPunishmentReason(event);
        String appealReason = ModalAppealDiscord.getAppealReason(event);

        return MessageCreateSpec.create()
                .withFlags(Message.Flag.IS_COMPONENTS_V2)
                .withComponents(Container.of(
                        TextDisplay.of("# :information_source:  CASE DETAILS"),
                        TextDisplay.of("**Case ID:** " + "`" + caseId + "`"),
                        TextDisplay.of("**Game:** " + gameName),
                        Separator.of(),
                        TextDisplay.of("**Punishment Type:** " + punishmentType.name()),
                        TextDisplay.of("**Punishment Reason:** \n> " + punishmentReason),
                        TextDisplay.of("**Appeal Reason:** \n> " + appealReason)));
    }
}
