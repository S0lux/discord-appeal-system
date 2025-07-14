package com.sopuro.appeal_system.exceptions.appeal;

import com.sopuro.appeal_system.exceptions.AppealSystemException;

public class CategoryAlreadySetupException extends AppealSystemException {
    public CategoryAlreadySetupException() {
        super("Categories for open and/or closed appeals are already set up.");
    }
}
