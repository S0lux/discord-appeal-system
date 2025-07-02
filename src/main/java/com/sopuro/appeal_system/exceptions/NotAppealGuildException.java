package com.sopuro.appeal_system.exceptions;

public class NotAppealGuildException extends AppealSystemException {
    public NotAppealGuildException(String guildId) {
        super("Guild with ID " + guildId + " is not configured for the appeal system.");
    }
}
