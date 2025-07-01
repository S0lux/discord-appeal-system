package com.sopuro.appeal_system.commands;

import com.sopuro.appeal_system.configs.AppealSystemConfig;
import com.sopuro.appeal_system.exceptions.AppealSystemException;
import com.sopuro.appeal_system.exceptions.ApplicationMisconfiguredException;
import com.sopuro.appeal_system.exceptions.MissingGuildContextException;
import com.sopuro.appeal_system.exceptions.MissingPermissionException;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Slf4j
public class CommandListener {
    private final Map<String, SlashCommand> commandMap;
    private final AppealSystemConfig appealSystemConfig;

    public CommandListener(List<SlashCommand> slashCommands, GatewayDiscordClient client, AppealSystemConfig appealSystemConfig) {
        this.appealSystemConfig = appealSystemConfig;
        this.commandMap = slashCommands.stream()
                .collect(Collectors.toMap(SlashCommand::getName, Function.identity()));

        client.on(ChatInputInteractionEvent.class, this::handle).subscribe();
    }

    public Mono<Void> handle(ChatInputInteractionEvent event) {
        String commandName = event.getCommandName();
        SlashCommand command = commandMap.get(commandName);

        if (command == null) {
            log.warn("Unknown command received: {}", commandName);
            return event.reply("Unknown command.").withEphemeral(true);
        }

        return validateGuildContext(event)
                .then(Mono.defer(() -> validatePermissions(event, command.getRoles())))
                .then(Mono.defer(() -> executeCommand(event, command)))
                .onErrorResume(error -> handleCommandError(event, error));
    }

    private Mono<Void> validateGuildContext(ChatInputInteractionEvent event) {
        Optional<Snowflake> guildId = event.getInteraction().getGuildId();
        if (guildId.isEmpty()) return Mono.error(new MissingGuildContextException());
        return Mono.empty();
    }

    private Mono<Void> validatePermissions(ChatInputInteractionEvent event, List<String> requiredRoles) {
        if (requiredRoles.isEmpty()) {
            return Mono.empty();
        }

        String guildId = event.getInteraction().getGuildId()
                .orElseThrow(() -> new IllegalStateException("Guild ID should be present at this point"))
                .asString();

        try {
            List<String> requiredRoleIds = mapRolesToIds(requiredRoles, guildId);
            List<String> userRoleIds = getUserRoleIds(event);

            boolean hasPermission = requiredRoleIds.stream().anyMatch(userRoleIds::contains);
            if (!hasPermission) return Mono.error(new MissingPermissionException(requiredRoles));
        } catch (IllegalArgumentException e) {
            log.error("Role configuration error for guild {}: {}", guildId, e.getMessage());
            return Mono.error(new ApplicationMisconfiguredException());
        }

        return Mono.empty();
    }

    private List<String> mapRolesToIds(List<String> roles, String guildId) {
        return roles.stream()
                .map(role -> mapRoleToId(role, guildId))
                .toList();
    }

    private String mapRoleToId(String role, String guildId) {
        return switch (role.toUpperCase()) {
            case "OVERSEER" -> appealSystemConfig.getOverseerRoleId(guildId);
            case "JUDGE" -> appealSystemConfig.getJudgeRoleId(guildId);
            default -> throw new IllegalArgumentException("Unknown role: " + role);
        };
    }

    private List<String> getUserRoleIds(ChatInputInteractionEvent event) {
        return event.getInteraction().getMember()
                .map(member -> member.getRoleIds().stream()
                        .map(Snowflake::asString)
                        .toList())
                .orElse(List.of());
    }

    private Mono<Void> executeCommand(ChatInputInteractionEvent event, SlashCommand command) {
        return command.handle(event)
                .doOnSuccess(ignored -> log.debug("Command {} executed successfully for user {}",
                        command.getName(),
                        event.getInteraction().getUser().getId().asString()));
    }

    private Mono<Void> handleCommandError(ChatInputInteractionEvent event, Throwable error) {
        if (error instanceof AppealSystemException)
            return event.reply(error.getMessage())
                    .withEphemeral(true);

        return event.reply("An error occurred while processing your command. Please try again later.")
                .withEphemeral(true);
    }
}