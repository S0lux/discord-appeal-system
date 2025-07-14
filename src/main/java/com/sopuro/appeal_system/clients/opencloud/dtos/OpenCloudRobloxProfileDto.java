package com.sopuro.appeal_system.clients.opencloud.dtos;

import java.time.Instant;

public record OpenCloudRobloxProfileDto(
        String id, String name, String displayName, boolean premium, boolean idVerified, Instant createdTime) {}
