package com.sopuro.appeal_system.clients.opencloud.dtos;

import java.time.Instant;

public record RobloxGameJoinRestrictionDto(
        Boolean active,
        Instant startTime,
        String duration,
        String privateReason,
        String publicReason,
        Boolean excludeAltAccounts
) {}
