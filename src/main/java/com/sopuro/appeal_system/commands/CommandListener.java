package com.sopuro.appeal_system.commands;

import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Component
@Slf4j
public class CommandListener {
    private final Collection<SlashCommand> commands;

    public CommandListener(List<SlashCommand> slashCommands, GatewayDiscordClient client) {
        commands = slashCommands;
        client.on(ChatInputInteractionEvent.class, this::handle).subscribe();
    }

    public Mono<Void> handle(ChatInputInteractionEvent event) {
        Optional<Snowflake> guildId = event.getInteraction().getGuildId();
        if (guildId.isEmpty()) {
            log.warn("Received command in a DM or non-guild context from User: {}", event.getInteraction().getUser().getId().asString());
            return event.reply("This command can only be used in a server.").withEphemeral(true);
        }

        return Flux.fromIterable(commands)
                .filter(command -> command.getName().equals(event.getCommandName()))
                .next()
                .flatMap(command -> command.handle(event));
    }
}