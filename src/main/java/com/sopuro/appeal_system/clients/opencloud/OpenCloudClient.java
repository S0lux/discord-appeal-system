package com.sopuro.appeal_system.clients.opencloud;

import com.sopuro.appeal_system.clients.opencloud.dtos.RobloxAvatarDto;
import com.sopuro.appeal_system.clients.opencloud.dtos.RobloxProfileDto;
import com.sopuro.appeal_system.clients.opencloud.dtos.RobloxUserRestrictionDto;
import com.sopuro.appeal_system.clients.opencloud.dtos.RobloxUserUnbanDto;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PatchExchange;

@HttpExchange
public interface OpenCloudClient {
    @GetExchange("/cloud/v2/users/{userId}")
    RobloxProfileDto getRobloxProfile(@PathVariable String userId);

    @GetExchange("/cloud/v2/users/{userId}:generateThumbnail?shape=SQUARE")
    RobloxAvatarDto getRobloxAvatar(@PathVariable String userId);

    @GetExchange("/cloud/v2/universes/{universeId}/user-restrictions/{userRestrictionId}")
    RobloxUserRestrictionDto getUserRestriction(
            @PathVariable String universeId, @PathVariable String userRestrictionId);

    @PatchExchange("/cloud/v2/universes/{universeId}/user-restrictions/{userRestrictionId}")
    RobloxUserRestrictionDto unbanUser(
            @PathVariable String universeId,
            @PathVariable String userRestrictionId,
            @RequestBody RobloxUserUnbanDto newRestriction);
}
