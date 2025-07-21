package com.sopuro.appeal_system.exceptions.rover;

import com.sopuro.appeal_system.exceptions.RoverException;

public class RobloxAccountNotVerifiedException extends RoverException {
    public RobloxAccountNotVerifiedException() {
        super("Roblox account is not verified. Please verify your account with Rover to proceed.");
    }
}
