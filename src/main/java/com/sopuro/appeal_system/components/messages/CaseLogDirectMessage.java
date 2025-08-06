package com.sopuro.appeal_system.components.messages;

import com.sopuro.appeal_system.entities.CaseEntity;
import com.sopuro.appeal_system.shared.enums.AppealVerdict;
import com.sopuro.appeal_system.shared.utils.EncryptionHelper;
import discord4j.core.spec.EmbedCreateFields;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.rest.util.Color;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Objects;

@Slf4j
public class CaseLogDirectMessage {
    public static MessageCreateSpec create(CaseEntity caseEntity, String frontendDomain) {
        String accessCode = Objects.requireNonNull(EncryptionHelper.generateCaseAccessCode(
                caseEntity.getId().toString(), caseEntity.getAppealerDiscordId()));

        return MessageCreateSpec.create()
                .withEmbeds(EmbedCreateSpec.create()
                        .withColor(caseEntity.getAppealVerdict() == AppealVerdict.ACCEPTED ? Color.GREEN : Color.RED)
                        .withTitle("Case Closed")
                        .withDescription(
                                ":warning: **IMPORTANT** :warning:\n* **Do not** share your case details with anyone that is not an appeal staff.\n* If you have to share your case details with an appeal staff, **only share the ID of your case.**\n* Intentional leaking of your appeal will result in negative consequences.")
                        .withFields(
                                EmbedCreateFields.Field.of(
                                        "Status", caseEntity.getAppealVerdict().toString(), true),
                                EmbedCreateFields.Field.of(
                                        "Access Link",
                                        String.format(
                                                "[Click here](https://%s/appeals/%s)", frontendDomain, accessCode),
                                        true),
                                EmbedCreateFields.Field.of("Reason", "> " + caseEntity.getVerdictReason(), false))
                        .withTimestamp(Instant.now())
                        .withFooter(EmbedCreateFields.Footer.of("ID: " + caseEntity.getId(), "")));
    }
}
