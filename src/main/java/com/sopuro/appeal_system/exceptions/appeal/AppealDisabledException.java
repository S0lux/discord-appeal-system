package com.sopuro.appeal_system.exceptions.appeal;

import com.sopuro.appeal_system.exceptions.AppealException;

public class AppealDisabledException extends AppealException {
    public AppealDisabledException() {
        super("We are currently not accepting new appeals right now.");
    }
}
