package com.sopuro.appeal_system.exceptions.appeal;

public class NotInCommunityServerException extends RuntimeException {
    public NotInCommunityServerException(String communityId) {
        super("The bot is not in community server: " + communityId + ".");
    }
}
