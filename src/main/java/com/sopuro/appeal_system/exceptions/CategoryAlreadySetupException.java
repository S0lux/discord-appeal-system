package com.sopuro.appeal_system.exceptions;

public class CategoryAlreadySetupException extends AppealSystemException {
    public CategoryAlreadySetupException() {
        super("Categories for open and/or closed appeals are already set up.");
    }
}
