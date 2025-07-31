package com.sopuro.appeal_system.dtos;

import java.util.List;

public record GameConfigDto(
        String name,
        List<String> crossroadDescription,
        String image,
        String appealServerId,
        String communityServerId,
        String appealJudgeRoleId,
        String appealOverseerRoleId,
        String logChannelId) {
    public String normalizedName() {
        return name != null ? name.toLowerCase().replace(" ", "_") : null;
    }
}
