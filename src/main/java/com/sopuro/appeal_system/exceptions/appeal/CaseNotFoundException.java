package com.sopuro.appeal_system.exceptions.appeal;

import com.sopuro.appeal_system.exceptions.AppealException;

public class CaseNotFoundException extends AppealException {
    public CaseNotFoundException() {
        super("Case does not exist or access code is invalid");
    }
}
