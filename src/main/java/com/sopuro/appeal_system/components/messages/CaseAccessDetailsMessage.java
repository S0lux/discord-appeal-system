package com.sopuro.appeal_system.components.messages;

import com.sopuro.appeal_system.entities.CaseEntity;
import discord4j.core.spec.EmbedCreateFields;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.rest.util.Color;

import java.time.Instant;

public class CaseAccessDetailsMessage {
    public static MessageCreateSpec create(CaseEntity caseEntity, String accessDomain, String accessCode) {
        return MessageCreateSpec.create()
                .withEmbeds(EmbedCreateSpec.create()
                        .withTitle("Case Access Details")
                        .withDescription("**Do not share these information**, they are for your eyes only.")
                        .withFields(EmbedCreateFields.Field.of(
                                "Access Link",
                                String.format("[Here](https://%s/appeals/%s)", accessDomain, accessCode),
                                false))
                        .withTimestamp(Instant.now())
                        .withFooter(EmbedCreateFields.Footer.of("Case ID: " + caseEntity.getId(), null))
                        .withColor(Color.SUMMER_SKY));
    }
}
