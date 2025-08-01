package com.sopuro.appeal_system.dtos;

import java.time.Instant;

public record GenericErrorResponse(
        int statusCode,
        String path,
        String message,
        Instant timestamp
) {}
