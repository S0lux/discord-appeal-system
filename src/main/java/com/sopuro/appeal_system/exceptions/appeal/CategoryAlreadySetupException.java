package com.sopuro.appeal_system.exceptions.appeal;

import com.sopuro.appeal_system.exceptions.AppealException;

public class CategoryAlreadySetupException extends AppealException {
    public CategoryAlreadySetupException() {
        super("Categories for open and/or closed appeals are already set up.");
    }
}
