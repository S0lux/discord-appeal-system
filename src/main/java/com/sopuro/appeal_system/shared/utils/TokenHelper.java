package com.sopuro.appeal_system.shared.utils;

import com.sopuro.appeal_system.shared.enums.ServerType;

public class TokenHelper {
    public static String retrieveRoverTokenForGame(String gameName, ServerType serverType) {
        return switch (serverType) {
            case APPEAL -> System.getenv("APPEAL_" + gameName.replace(" ", "_").toUpperCase() + "_ROVER_TOKEN");
            case COMMUNITY ->
                System.getenv("COMMUNITY_" + gameName.replace(" ", "_").toUpperCase() + "_ROVER_TOKEN");
        };
    }
}
