package com.sopuro.appeal_system.exceptions.appeal;

import com.sopuro.appeal_system.exceptions.AppealException;

public class AppealAlreadyCompletedException extends AppealException {
    public AppealAlreadyCompletedException() {
        super("This appeal has already been completed.");
    }
}
