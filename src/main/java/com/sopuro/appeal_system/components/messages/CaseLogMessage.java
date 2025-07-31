package com.sopuro.appeal_system.components.messages;

import com.sopuro.appeal_system.clients.opencloud.dtos.OpenCloudRobloxAvatarDto;
import com.sopuro.appeal_system.clients.opencloud.dtos.OpenCloudRobloxProfileDto;
import com.sopuro.appeal_system.entities.CaseEntity;
import com.sopuro.appeal_system.shared.enums.AppealVerdict;
import discord4j.common.util.TimestampFormat;
import discord4j.core.object.component.*;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.rest.util.Color;

public class CaseLogMessage {

    public static MessageCreateSpec create(
            CaseEntity caseEntity,
            OpenCloudRobloxProfileDto robloxProfileDto,
            OpenCloudRobloxAvatarDto robloxAvatarDto) {

        Color containerColor = caseEntity.getAppealVerdict() == AppealVerdict.ACCEPTED ? Color.GREEN : Color.RED;
        String header = caseEntity.getAppealVerdict() == AppealVerdict.ACCEPTED
                ? "# :white_check_mark:  APPEAL ACCEPTED"
                : "# :x:  APPEAL REJECTED";

        return MessageCreateSpec.create()
                .withFlags(Message.Flag.IS_COMPONENTS_V2)
                .withComponents(Container.of(
                                TextDisplay.of(header),
                                TextDisplay.of("**Appeal ID: **" + "`" + caseEntity.getId() + "`"),
                                TextDisplay.of(
                                        "**Appealer Discord: **" + " <@" + caseEntity.getAppealerDiscordId() + ">"),
                                TextDisplay.of("**Game: **" + caseEntity.getGame()),
                                TextDisplay.of("**Type: **" + caseEntity.getPunishmentType()),
                                TextDisplay.of("**Closed by: **" + " <@" + caseEntity.getVerdictBy() + ">"),
                                TextDisplay.of(
                                        "**Closed on: **" + TimestampFormat.LONG_DATE.format(caseEntity.getClosedAt())),
                                TextDisplay.of("**Reason:** \n> " + caseEntity.getVerdictReason()),
                                Separator.of(),
                                TextDisplay.of("# :video_game:  APPEALER ROBLOX PROFILE"),
                                Section.of(
                                        Thumbnail.of(UnfurledMediaItem.of(
                                                robloxAvatarDto.response().imageUri())),
                                        TextDisplay.of("**ID:** " + "`" + robloxProfileDto.id() + "`"),
                                        TextDisplay.of("**Username:** " + robloxProfileDto.name()),
                                        TextDisplay.of("**Display Name:** " + robloxProfileDto.displayName())))
                        .withColor(containerColor));
    }
}
