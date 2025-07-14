package com.sopuro.appeal_system.exceptions.appeal;

import com.sopuro.appeal_system.exceptions.AppealSystemException;
import com.sopuro.appeal_system.shared.enums.AppealRole;

import java.util.List;

public class MissingPermissionException extends AppealSystemException {
    public MissingPermissionException(List<AppealRole> rolesWithPermission) {
        super("Only users with the following roles can use this command: " +
                String.join(
                        ", ",
                        rolesWithPermission.stream().map(Enum::name).toList()));
    }

    public MissingPermissionException(String roleWithPermission) {
        super("Only users with the following role can use this command: " + roleWithPermission);
    }

    public MissingPermissionException() {
        super("You do not have permission to use this command.");
    }
}
