package com.sopuro.appeal_system.components.messages;

import com.sopuro.appeal_system.entities.CaseEntity;
import discord4j.core.object.component.Container;
import discord4j.core.object.component.Separator;
import discord4j.core.object.component.TextDisplay;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.MessageCreateSpec;

public class CaseInfoMessage {

    public static MessageCreateSpec create(CaseEntity caseEntity) {
        Container container = Container.of(
                TextDisplay.of("# :information_source:  CASE DETAILS"),
                TextDisplay.of("**Case ID:** " + "`" + caseEntity.getId() + "`"),
                TextDisplay.of("**Game:** " + caseEntity.getGame()),
                Separator.of(),
                TextDisplay.of("**Punishment Type:** " + caseEntity.getPunishmentType()),
                TextDisplay.of("**Punishment Platform:** " + caseEntity.getAppealPlatform()),
                TextDisplay.of("**Punishment Reason:** \n> " + caseEntity.getPunishmentReason()),
                TextDisplay.of("**Appeal Reason:** \n> " + caseEntity.getAppealReason()));

        container = caseEntity.getVideoUrl() != null
                ? container.withAddedComponent(TextDisplay.of("**Appeal Video:** \n> " + caseEntity.getVideoUrl()))
                : container;

        return MessageCreateSpec.create()
                .withFlags(Message.Flag.IS_COMPONENTS_V2)
                .withComponents(container);
    }
}
