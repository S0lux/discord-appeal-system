package com.sopuro.appeal_system.exceptions.appeal;

import com.sopuro.appeal_system.exceptions.AppealException;

public class IncorrectServerSetupException extends AppealException {
    public IncorrectServerSetupException() {
        super("This server is not set up correctly. Please contact the server administrator to re-setup the server.");
    }
}
