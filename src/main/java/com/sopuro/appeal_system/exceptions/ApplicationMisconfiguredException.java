package com.sopuro.appeal_system.exceptions;

public class ApplicationMisconfiguredException extends AppealSystemException {
    public ApplicationMisconfiguredException() {
        super("Application is misconfigured.");
    }
}
