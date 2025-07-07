package com.sopuro.appeal_system.clients.rover.dtos;

import java.util.List;

public record RobloxToDiscordDto(List<PartialGuildMemberDto> discordUsers, String robloxId, String guildId) {}
