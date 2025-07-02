package com.sopuro.appeal_system.commands;

import com.sopuro.appeal_system.shared.enums.AppealRole;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import reactor.core.publisher.Mono;

import java.util.List;

public interface SlashCommand {
    String getName();
    List<AppealRole> allowedRoles();
    Mono<Void> preCondition(ChatInputInteractionEvent event);
    Mono<Void> handle(ChatInputInteractionEvent event);
}
