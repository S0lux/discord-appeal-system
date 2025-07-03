package com.sopuro.appeal_system.exceptions;

public class UserIsNotDiscordBannedException extends AppealSystemException {
    public UserIsNotDiscordBannedException() {
        super("You are not banned from the Discord Community server.\n");
    }
}
