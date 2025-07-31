package com.sopuro.appeal_system.components.menus;

import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Container;
import discord4j.core.object.component.SelectMenu;
import discord4j.core.object.component.TextDisplay;
import discord4j.core.spec.InteractionApplicationCommandCallbackSpec;

import java.util.List;

public class MenuAppealDiscord {
    public static final String BAN_VALUE = "ban";
    public static final String WARNING_VALUE = "warning";
    public static final String DISCORD_SELECT_MENU = "crossroads:discord_select_menu";
    private static final String TITLE = "## Discord Appeal";
    private static final String DESCRIPTION = "Please select the type of appeal you want to submit:";
    private static final String BAN_LABEL = "Permanent/Temporary Ban Appeal";
    private static final String WARNING_LABEL = "Warning Appeal";

    public static InteractionApplicationCommandCallbackSpec createSelectMenu() {
        return InteractionApplicationCommandCallbackSpec
                .create()
                .withEphemeral(true)
                .withComponents(
                        Container.of(
                                TextDisplay.of(TITLE),
                                TextDisplay.of(DESCRIPTION),
                                ActionRow.of(
                                        SelectMenu.of(
                                                DISCORD_SELECT_MENU,
                                                List.of(
                                                        SelectMenu.Option.of(BAN_LABEL, BAN_VALUE),
                                                        SelectMenu.Option.of(WARNING_LABEL, WARNING_VALUE)
                                                )
                                        )
                                )
                        )
                );
    }
}