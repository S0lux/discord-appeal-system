package com.sopuro.appeal_system.components.messages;

import com.sopuro.appeal_system.clients.opencloud.dtos.OpenCloudRobloxAvatarDto;
import com.sopuro.appeal_system.clients.opencloud.dtos.OpenCloudRobloxProfileDto;
import discord4j.common.util.TimestampFormat;
import discord4j.core.object.component.*;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.MessageCreateSpec;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RobloxProfileMessage {

    @Value("${appeal-system.emojis.roblox}")
    private String robloxEmojiValue;

    private static String robloxEmoji;

    @PostConstruct
    public void init() {
        robloxEmoji = robloxEmojiValue;
    }

    public static MessageCreateSpec create(
            OpenCloudRobloxProfileDto robloxProfileDto, OpenCloudRobloxAvatarDto robloxAvatarDto) {
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