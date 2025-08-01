package com.sopuro.appeal_system.clients.opencloud.dtos;

public record RobloxUserUnbanDto(
        RobloxGameJoinUnbanDto gameJoinRestriction
) {
    public record RobloxGameJoinUnbanDto(
            boolean active
    ) {}
}
