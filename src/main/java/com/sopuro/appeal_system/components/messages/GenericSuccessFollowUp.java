package com.sopuro.appeal_system.components.messages;

import discord4j.core.object.component.Container;
import discord4j.core.object.component.Separator;
import discord4j.core.object.component.TextDisplay;
import discord4j.core.spec.InteractionFollowupCreateSpec;
import discord4j.rest.util.Color;

public class GenericSuccessFollowUp {
    public static InteractionFollowupCreateSpec create(String message) {
        return InteractionFollowupCreateSpec.create()
                .withEphemeral(true)
                .withComponents(Container.of(
                                TextDisplay.of("## :white_check_mark:  Command completed successfully"),
                                Separator.of(),
                                TextDisplay.of("**Message:** " + message))
                        .withColor(Color.GREEN));
    }
}
