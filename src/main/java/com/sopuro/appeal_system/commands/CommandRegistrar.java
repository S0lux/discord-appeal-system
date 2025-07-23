package com.sopuro.appeal_system.commands;

import com.sopuro.appeal_system.configs.AppealSystemConfig;
import discord4j.common.JacksonResources;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.rest.RestClient;
import discord4j.rest.http.client.ClientException;
import discord4j.rest.service.ApplicationService;
import io.netty.handler.codec.http.HttpResponseStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;

@Component
@DependsOn("configurationIntegrityChecker")
@RequiredArgsConstructor
@Slf4j
public class CommandRegistrar implements ApplicationRunner {

    private static final Set<String> APPEAL_ONLY_COMMANDS = Set.of("appeals", "panel", "setup", "verdict");
    private static final String COMMANDS_PATTERN = "commands/*.json";
    private static final Duration REGISTRATION_TIMEOUT = Duration.ofSeconds(30);

    private final RestClient discordRestClient;
    private final AppealSystemConfig appealSystemConfig;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try {
            log.info("Starting Discord command registration...");

            final long applicationId = getApplicationId();
            final List<ApplicationCommandRequest> commands = loadCommands();

            registerAppealOnlyCommands(applicationId, commands);
            registerGlobalCommands(applicationId, commands);

            log.info("Command registration completed successfully");

        } catch (Exception e) {
            log.error("Failed to register Discord commands", e);
            throw e;
        }
    }

    private long getApplicationId() {
        return discordRestClient
                .getApplicationId()
                .timeout(REGISTRATION_TIMEOUT)
                .doOnError(TimeoutException.class, e -> log.error("Timeout while fetching application ID"))
                .blockOptional()
                .orElseThrow(() -> new IllegalStateException("Could not retrieve Discord application ID"));
    }

    private List<ApplicationCommandRequest> loadCommands() throws IOException {
        log.info("Loading command definitions from resources...");

        final JacksonResources d4jMapper = JacksonResources.create();
        final PathMatchingResourcePatternResolver matcher = new PathMatchingResourcePatternResolver();
        final Resource[] resources = matcher.getResources(COMMANDS_PATTERN);

        if (resources.length == 0) {
            throw new IllegalStateException("No command files found in " + COMMANDS_PATTERN);
        }

        return Flux.fromArray(resources)
                .flatMap(resource -> {
                    try {
                        ApplicationCommandRequest request = d4jMapper
                                .getObjectMapper()
                                .readValue(resource.getInputStream(), ApplicationCommandRequest.class);
                        log.debug("Loaded command: {}", request.name());
                        return Mono.just(request);
                    } catch (IOException e) {
                        log.error("Failed to parse command file: {}", resource.getFilename(), e);
                        return Mono.error(e);
                    }
                })
                .collectList()
                .block();
    }

    private void registerAppealOnlyCommands(long applicationId, List<ApplicationCommandRequest> allCommands) {
        final List<ApplicationCommandRequest> appealOnlyCommands = allCommands.stream()
                .filter(command -> APPEAL_ONLY_COMMANDS.contains(command.name()))
                .toList();

        log.info(
                "Registering {} appeal-only commands to {} guild(s)",
                appealOnlyCommands.size(),
                appealSystemConfig.getAppealServerIds().size());

        final ApplicationService applicationService = discordRestClient.getApplicationService();

        Flux.fromIterable(appealSystemConfig.getAppealServerIds())
                .flatMap(guildId ->
                        registerCommandsForGuild(applicationService, applicationId, guildId, appealOnlyCommands))
                .doOnComplete(() -> log.info("All appeal-only commands registered successfully"))
                .doOnError(error -> log.error("Some appeal-only command registrations failed", error))
                .blockLast();
    }

    private Mono<Void> registerCommandsForGuild(
            ApplicationService applicationService,
            long applicationId,
            String guildId,
            List<ApplicationCommandRequest> commands) {
        return applicationService
                .bulkOverwriteGuildApplicationCommand(applicationId, Long.parseLong(guildId), commands)
                .timeout(REGISTRATION_TIMEOUT)
                .doOnSubscribe(sub -> log.debug("Registering {} commands for guild: {}", commands.size(), guildId))
                .doOnComplete(() -> log.info("Successfully registered commands for guild: {}", guildId))
                .doOnError(
                        TimeoutException.class,
                        error -> log.error("Timeout registering commands for guild: {}", guildId))
                .doOnError(NumberFormatException.class, error -> log.error("Invalid guild ID format: {}", guildId))
                .doOnError(ClientException.class, error -> {
                    switch (error.getStatus().code()) {
                        case 400 -> log.error("Bad request error while registering commands for guild: {}", guildId);
                        case 403 -> log.error("Bot is not in the server to register commands for guild: {}", guildId);
                        default ->
                            log.error("Unexpected error while registering commands for guild: {}", guildId, error);
                    }
                })
                .doOnError(
                        error -> !(error instanceof TimeoutException
                                || error instanceof NumberFormatException
                                || error instanceof ClientException),
                        error -> log.error("Failed to register commands for guild: {}", guildId, error))
                .onErrorResume(error -> {
                    log.warn("Continuing with remaining guilds after failure for guild: {}", guildId);
                    return Mono.empty();
                })
                .then();
    }

    private void registerGlobalCommands(long applicationId, List<ApplicationCommandRequest> allCommands) {
        final List<ApplicationCommandRequest> globalCommands = allCommands.stream()
                .filter(command -> !APPEAL_ONLY_COMMANDS.contains(command.name()))
                .toList();

        log.info("Registering {} global commands", globalCommands.size());

        final ApplicationService applicationService = discordRestClient.getApplicationService();

        applicationService
                .bulkOverwriteGlobalApplicationCommand(applicationId, globalCommands)
                .timeout(REGISTRATION_TIMEOUT)
                .doOnComplete(() -> log.info("Successfully registered {} global commands", globalCommands.size()))
                .doOnError(TimeoutException.class, error -> log.error("Timeout while registering global commands"))
                .doOnError(
                        error -> !(error instanceof TimeoutException),
                        error -> log.error("Failed to register global commands", error))
                .subscribe();
    }
}