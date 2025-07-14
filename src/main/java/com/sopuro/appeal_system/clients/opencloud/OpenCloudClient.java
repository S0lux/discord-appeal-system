package com.sopuro.appeal_system.clients.opencloud;

import com.sopuro.appeal_system.clients.opencloud.dtos.OpenCloudRobloxAvatarDto;
import com.sopuro.appeal_system.clients.opencloud.dtos.OpenCloudRobloxProfileDto;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange
public interface OpenCloudClient {
    @GetExchange("/cloud/v2/users/{userId}")
    OpenCloudRobloxProfileDto getRobloxProfile(@PathVariable String userId);

    @GetExchange("/cloud/v2/users/{userId}:generateThumbnail?shape=SQUARE")
    OpenCloudRobloxAvatarDto getRobloxAvatar(@PathVariable String userId);
}
