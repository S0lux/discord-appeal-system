package com.sopuro.appeal_system.clients.opencloud.dtos;

import java.time.Instant;

public record RobloxUserRestrictionDto(
        String user,
        RobloxGameJoinRestrictionDto gameJoinRestriction,
        Instant updateTime
) {}
