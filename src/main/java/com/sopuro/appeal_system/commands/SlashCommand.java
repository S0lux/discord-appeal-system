package com.sopuro.appeal_system.commands;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import reactor.core.publisher.Mono;

import java.util.List;

public interface SlashCommand {

    String getName();
    List<String> getRoles();
    Mono<Void> handle(ChatInputInteractionEvent event);
}
