package com.sopuro.appeal_system.components.messages;

import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.InteractionReplyEditSpec;
import discord4j.rest.util.Color;

public class GenericErrorMessageEmbed {
    public static InteractionReplyEditSpec create(String message) {
        return InteractionReplyEditSpec.create()
                .withEmbeds(EmbedCreateSpec.create()
                        .withColor(Color.RED)
                        .withTitle("Something went wrong" + " :broken_chain:")
                        .withDescription(message));
    }
}
