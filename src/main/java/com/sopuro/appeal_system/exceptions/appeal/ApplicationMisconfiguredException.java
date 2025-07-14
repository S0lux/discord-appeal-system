package com.sopuro.appeal_system.exceptions.appeal;

import com.sopuro.appeal_system.exceptions.AppealSystemException;

public class ApplicationMisconfiguredException extends AppealSystemException {
    public ApplicationMisconfiguredException() {
        super("Application is misconfigured.");
    }
}
