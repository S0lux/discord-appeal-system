package com.sopuro.appeal_system.components.messages;

import com.sopuro.appeal_system.clients.opencloud.dtos.RobloxAvatarDto;
import com.sopuro.appeal_system.clients.opencloud.dtos.RobloxProfileDto;
import discord4j.common.util.TimestampFormat;
import discord4j.core.object.component.*;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.MessageCreateSpec;
import org.springframework.stereotype.Component;

@Component
public class RobloxProfileMessage {
    private static String robloxEmoji;

    public static MessageCreateSpec create(
            RobloxProfileDto robloxProfileDto, RobloxAvatarDto robloxAvatarDto) {
        return MessageCreateSpec.create()
                .withFlags(Message.Flag.IS_COMPONENTS_V2)
                .withComponents(Container.of(
                        TextDisplay.of("# " + robloxEmoji + "  ROBLOX PROFILE"),
                        Section.of(
                                Thumbnail.of(UnfurledMediaItem.of(robloxAvatarDto.response().imageUri())),
                                TextDisplay.of("**ID:** " + "`" + robloxProfileDto.id() + "`"),
                                TextDisplay.of("**Username:** " + robloxProfileDto.name()),
                                TextDisplay.of("**Display Name:** " + robloxProfileDto.displayName())),
                        Separator.of(),
                        TextDisplay.of("**Premium:** " + (robloxProfileDto.premium() ? ":white_check_mark:" : ":x:")),
                        TextDisplay.of("**ID Verified:** " + (robloxProfileDto.idVerified() ? ":white_check_mark:" : ":x:")),
                        TextDisplay.of("**Account Creation Time:** " + TimestampFormat.LONG_DATE.format(robloxProfileDto.createTime())),
                        Separator.of(),
                        ActionRow.of(
                                Button.link(
                                        "https://roblox.com/users/" + robloxProfileDto.id() + "/profile",
                                        "View Profile"
                                )
                        )
                ));
    }
}