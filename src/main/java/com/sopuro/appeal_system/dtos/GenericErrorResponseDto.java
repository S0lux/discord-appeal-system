package com.sopuro.appeal_system.dtos;

import java.time.Instant;

public record GenericErrorResponseDto(
        int statusCode,
        String path,
        String message,
        Instant timestamp
) {}
