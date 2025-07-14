package com.sopuro.appeal_system.exceptions.rover;

import com.sopuro.appeal_system.exceptions.RoverClientException;

public class RobloxAccountNotVerifiedException extends RoverClientException {
    public RobloxAccountNotVerifiedException() {
        super("Roblox account is not verified. Please verify your account with Rover to proceed.");
    }
}
