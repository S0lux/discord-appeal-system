package com.sopuro.appeal_system.components.messages;

import discord4j.core.object.component.Container;
import discord4j.core.object.component.Separator;
import discord4j.core.object.component.TextDisplay;
import discord4j.core.spec.InteractionFollowupCreateSpec;
import discord4j.rest.util.Color;

public class GenericErrorFollowUp {
    public static InteractionFollowupCreateSpec create(String message, boolean isExpected) {
        String title = isExpected ? "Your command did not pass some checks" : "Something went wrong";
        return InteractionFollowupCreateSpec.create()
                .withEphemeral(true)
                .withComponents(Container.of(
                                TextDisplay.of("## :broken_chain:  " + title),
                                Separator.of(),
                                TextDisplay.of("**Reason:** " + message))
                        .withColor(Color.RED));
    }
}
