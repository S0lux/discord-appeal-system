package com.sopuro.appeal_system.clients.rover.dtos;

import java.time.Instant;
import java.util.List;

public record RoverBanDto(
        Instant createdAt,
        List<String> discordIds,
        String reason,
        int robloxId) {
}
