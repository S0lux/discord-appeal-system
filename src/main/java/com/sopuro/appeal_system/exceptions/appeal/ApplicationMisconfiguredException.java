package com.sopuro.appeal_system.exceptions.appeal;

import com.sopuro.appeal_system.exceptions.AppealException;

public class ApplicationMisconfiguredException extends AppealException {
    public ApplicationMisconfiguredException() {
        super("Application is misconfigured.");
    }
}
