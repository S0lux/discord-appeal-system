package com.sopuro.appeal_system.components.messages;

import com.sopuro.appeal_system.shared.utils.EncryptionHelper;
import discord4j.common.util.TimestampFormat;
import discord4j.core.object.component.Container;
import discord4j.core.object.component.TextDisplay;
import discord4j.core.object.entity.Message;
import discord4j.core.spec.MessageCreateSpec;

public class AccessCodeDetailsMessage {
    public static MessageCreateSpec create(EncryptionHelper.CaseAccessDetails accessDetails) {
        return MessageCreateSpec.create()
                .withFlags(Message.Flag.IS_COMPONENTS_V2)
                .withComponents(Container.of(
                        TextDisplay.of("## :ledger:  ACCESS DETAILS"),
                        TextDisplay.of("**Case ID:** " + accessDetails.caseId()),
                        TextDisplay.of("**Belongs to:** <@" + accessDetails.createdBy() + ">"),
                        TextDisplay.of("**Created at:** "
                                + TimestampFormat.SHORT_DATE_TIME.format(accessDetails.creationTimestamp()))));
    }
}
