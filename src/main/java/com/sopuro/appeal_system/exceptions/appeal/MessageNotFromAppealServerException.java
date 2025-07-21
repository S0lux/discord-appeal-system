package com.sopuro.appeal_system.exceptions.appeal;

import com.sopuro.appeal_system.exceptions.AppealException;

public class MessageNotFromAppealServerException extends AppealException {
    public MessageNotFromAppealServerException(String messageId, String guildId) {
        super(String.format("Message with ID %s is not from an appeal server (Guild ID: %s)", messageId, guildId));
    }
}
