package com.sopuro.appeal_system.exceptions.appeal;

import com.sopuro.appeal_system.exceptions.AppealSystemException;

public class AppealDisabledException extends AppealSystemException {
    public AppealDisabledException() {
        super("We are currently not accepting new appeals right now.");
    }
}
