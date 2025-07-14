package com.sopuro.appeal_system.exceptions.appeal;

import com.sopuro.appeal_system.exceptions.AppealSystemException;

public class ExistingPendingCaseException extends AppealSystemException {
    public ExistingPendingCaseException() {
        super("You already have a pending appeal for this game and punishment type.");
    }
}
