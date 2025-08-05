package com.sopuro.appeal_system.exceptions.appeal;

import com.sopuro.appeal_system.exceptions.AppealException;

public class ApiTooManyRequestException extends AppealException {
    public ApiTooManyRequestException() {
        super("The server is currently overloaded. Please try again later.");
    }
}
