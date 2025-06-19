package com.sopuro.appeal_system.commands;

import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;

@Component
@Slf4j
public class CommandListener {
    private final Collection<SlashCommand> commands;

    public CommandListener(List<SlashCommand> slashCommands, GatewayDiscordClient client) {
        commands = slashCommands;
        client.on(ChatInputInteractionEvent.class, this::handle).subscribe();
    }

    public Mono<Void> handle(ChatInputInteractionEvent event) {
        return Flux.fromIterable(commands)
                .filter(command -> command.getName().equals(event.getCommandName()))
                .next()
                .flatMap(command -> command.handle(event));
    }
}