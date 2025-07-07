package com.sopuro.appeal_system.clients.rover.dtos;

import java.util.List;

public record PartialGuildMemberDto(String avatar, List<String> roles, String nick, PartialDiscordUserDto user) {}
