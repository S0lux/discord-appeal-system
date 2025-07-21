package com.sopuro.appeal_system.exceptions.appeal;

import com.sopuro.appeal_system.exceptions.AppealException;

public class ExistingPendingCaseException extends AppealException {
    public ExistingPendingCaseException() {
        super("You already have a pending appeal for this game and punishment type.");
    }
}
