package com.sopuro.appeal_system.exceptions.appeal;

import com.sopuro.appeal_system.exceptions.AppealException;

public class UserIsNotDiscordBannedException extends AppealException {
    public UserIsNotDiscordBannedException() {
        super("You are not banned from the Discord Community server.\n");
    }
}
