package com.sopuro.appeal_system.exceptions;

public class MissingGuildContextException extends AppealSystemException {
    public MissingGuildContextException() {
        super("This command can only be used in a server context.");
    }
}
