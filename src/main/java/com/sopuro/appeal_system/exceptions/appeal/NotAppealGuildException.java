package com.sopuro.appeal_system.exceptions.appeal;

import com.sopuro.appeal_system.exceptions.AppealException;

public class NotAppealGuildException extends AppealException {
    public NotAppealGuildException(String guildId) {
        super("Guild with ID " + guildId + " is not configured for the appeal system.");
    }
}
