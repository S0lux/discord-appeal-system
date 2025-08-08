package com.sopuro.appeal_system.components.messages;

import com.sopuro.appeal_system.entities.CaseEntity;
import discord4j.common.util.TimestampFormat;
import discord4j.core.object.component.Container;
import discord4j.core.object.component.Separator;
import discord4j.core.object.component.TextDisplay;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.MessageCreateSpec;

import java.util.ArrayList;
import java.util.List;

public class CaseHistoryMessage {

    public static MessageCreateSpec create(List<CaseEntity> caseEntities, String robloxId, String robloxUsername) {
        Container container = Container.of(
                TextDisplay.of("# :card_box:  APPEAL HISTORY"),
                TextDisplay.of(
                        "-# Displaying 10 most recent appeals for " + robloxUsername + " (Roblox ID: " + robloxId + ")"),
                Separator.of());

        List<TextDisplay> textDisplays = new ArrayList<>();

        if (caseEntities.isEmpty()) {
            textDisplays.add(TextDisplay.of("- No appeal found for this user."));
        } else {
            caseEntities.stream().limit(10).forEach(caseEntity -> {
                String caseInfo = String.format(
                        "- `%s` | %s | %s | %s | %s",
                        caseEntity.getId(),
                        caseEntity.getGame(),
                        caseEntity.getPunishmentType(),
                        caseEntity.getAppealVerdict(),
                        TimestampFormat.SHORT_DATE.format(caseEntity.getAppealedAt()));
                textDisplays.add(TextDisplay.of(caseInfo));
            });
        }

        return MessageCreateSpec.builder()
                .flags(Message.Flag.IS_COMPONENTS_V2)
                .components(container.withAddedComponents(textDisplays.toArray(new TextDisplay[0])))
                .build();
    }
}
