package com.sopuro.appeal_system.clients.opencloud.dtos;

import java.time.Instant;

public record RobloxProfileDto(
        String id, String name, String displayName, boolean premium, Instant createTime) {}
