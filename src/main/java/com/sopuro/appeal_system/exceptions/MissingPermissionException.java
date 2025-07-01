package com.sopuro.appeal_system.exceptions;

import java.util.List;

public class MissingPermissionException extends AppealSystemException {
    public MissingPermissionException(List<String> rolesWithPermission) {
        super("Only users with the following roles can use this command: " + String.join(", ", rolesWithPermission));
    }

    public MissingPermissionException(String roleWithPermission) {
        super("Only users with the following role can use this command: " + roleWithPermission);
    }

    public MissingPermissionException() {
        super("You do not have permission to use this command.");
    }
}
