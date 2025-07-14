package com.sopuro.appeal_system.exceptions.appeal;

import com.sopuro.appeal_system.exceptions.AppealSystemException;

public class MissingGuildContextException extends AppealSystemException {
    public MissingGuildContextException() {
        super("This command can only be used in a server context.");
    }
}
