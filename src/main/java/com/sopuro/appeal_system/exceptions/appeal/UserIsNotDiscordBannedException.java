package com.sopuro.appeal_system.exceptions.appeal;

import com.sopuro.appeal_system.exceptions.AppealException;

public class UserIsNotDiscordBannedException extends AppealException {
    public UserIsNotDiscordBannedException(String gameName) {
        super(String.format("You are not banned from the **%s** Discord Community server.\n", gameName));
    }
}
