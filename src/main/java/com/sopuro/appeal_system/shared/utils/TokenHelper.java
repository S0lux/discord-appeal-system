package com.sopuro.appeal_system.shared.utils;

public class TokenHelper {
    public static String retrieveRoverTokenForGame(String gameName) {
        String tokenEnvVar = "GAME_" + gameName.replace(" ", "_").toUpperCase() + "_ROVER_TOKEN";
        return System.getenv(tokenEnvVar);
    }
}
