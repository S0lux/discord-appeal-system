package com.sopuro.appeal_system.commands;

import discord4j.common.JacksonResources;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.rest.RestClient;
import discord4j.rest.service.ApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CommandRegistrar implements ApplicationRunner {
    private final RestClient discordRestClient;

    @Value("${discord.dev.guild.id}")
    private String devGuildId;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        final JacksonResources d4jMapper = JacksonResources.create();

        // Convenience variables for the sake of easier to read code below.
        PathMatchingResourcePatternResolver matcher = new PathMatchingResourcePatternResolver();
        @SuppressWarnings("DataFlowIssue")
        final long applicationId = discordRestClient.getApplicationId().block();
        final ApplicationService applicationService = discordRestClient.getApplicationService();


        //Get our commands JSON from resources as command data
        List<ApplicationCommandRequest> commands = new ArrayList<>();
        for (Resource resource : matcher.getResources("commands/*.json")) {
            ApplicationCommandRequest request = d4jMapper.getObjectMapper()
                    .readValue(resource.getInputStream(), ApplicationCommandRequest.class);

            commands.add(request);
        }

        if (devGuildId != null) {
            log.info("Registering commands to dev guild with ID: {}", devGuildId);
            applicationService.bulkOverwriteGuildApplicationCommand(applicationId, Long.parseLong(devGuildId), commands)
                    .doOnNext(ignore -> log.info("Successfully registered commands to dev guild"))
                    .doOnError(error -> log.error("Failed to register commands to dev guild", error))
                    .subscribe();
        } else {
            log.info("Registering commands globally");
            applicationService.bulkOverwriteGlobalApplicationCommand(applicationId, commands)
                    .doOnNext(ignore -> log.info("Successfully registered commands to GLOBAL"))
                    .doOnError(error -> log.error("Failed to register global commands", error))
                    .subscribe();
        }
    }
}
