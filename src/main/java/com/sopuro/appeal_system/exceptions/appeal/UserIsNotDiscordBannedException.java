package com.sopuro.appeal_system.exceptions.appeal;

import com.sopuro.appeal_system.exceptions.AppealSystemException;

public class UserIsNotDiscordBannedException extends AppealSystemException {
    public UserIsNotDiscordBannedException() {
        super("You are not banned from the Discord Community server.\n");
    }
}
