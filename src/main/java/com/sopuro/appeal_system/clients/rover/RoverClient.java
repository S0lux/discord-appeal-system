package com.sopuro.appeal_system.clients.rover;

import com.sopuro.appeal_system.clients.rover.dtos.DiscordToRobloxDto;
import com.sopuro.appeal_system.clients.rover.dtos.RobloxToDiscordDto;
import com.sopuro.appeal_system.clients.rover.dtos.RoverBanDto;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange
public interface RoverClient {
    @GetExchange("/guilds/{guildId}/discord-to-roblox/{discordUserId}")
    DiscordToRobloxDto toRoblox(
            @PathVariable String guildId,
            @PathVariable String discordUserId,
            @RequestHeader("Authorization") String token);

    @GetExchange("/guilds/{guildId}/roblox-to-discord/{robloxUserId}")
    RobloxToDiscordDto toDiscord(
            @PathVariable String guildId,
            @PathVariable String robloxUserId,
            @RequestHeader("Authorization") String token);

    @GetExchange("/guilds/{guildId}/bans/{robloxUserId}")
    RoverBanDto getBan(
            @PathVariable String guildId,
            @PathVariable String robloxUserId,
            @RequestHeader("Authorization") String token);

    @DeleteExchange("/guilds/{guildId}/bans/{robloxUserId}")
    Void deleteBan(
            @PathVariable String guildId,
            @PathVariable String robloxUserId,
            @RequestHeader("Authorization") String token);
}
