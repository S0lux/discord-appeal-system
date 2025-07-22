package com.sopuro.appeal_system.exceptions.appeal;

import com.sopuro.appeal_system.exceptions.AppealException;

public class NotAppealChannelException extends AppealException {
    public NotAppealChannelException() {
        super("This command can only be used in an appeal channel.");
    }
}
