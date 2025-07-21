package com.sopuro.appeal_system.exceptions.appeal;

import com.sopuro.appeal_system.exceptions.AppealException;

public class MissingGuildContextException extends AppealException {
    public MissingGuildContextException() {
        super("This command can only be used in a server context.");
    }
}
