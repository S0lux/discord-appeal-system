package com.sopuro.appeal_system.components.messages;

import com.sopuro.appeal_system.shared.utils.EncryptionHelper;
import discord4j.common.util.TimestampFormat;
import discord4j.core.spec.EmbedCreateFields;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.InteractionReplyEditSpec;

public class AccessCodeDetailsMessage {
    public static InteractionReplyEditSpec create(EncryptionHelper.CaseAccessDetails accessDetails) {
        return InteractionReplyEditSpec.create()
                .withEmbeds(EmbedCreateSpec.create()
                        .withTitle(":ledger: CASE DETAILS")
                        .withFields(
                                EmbedCreateFields.Field.of("Case ID", accessDetails.caseId(), false),
                                EmbedCreateFields.Field.of(
                                        "Belongs to", String.format("<@%s>", accessDetails.createdBy()), false),
                                EmbedCreateFields.Field.of(
                                        "Created at",
                                        TimestampFormat.SHORT_DATE.format(accessDetails.creationTimestamp()),
                                        false)));
    }
}
