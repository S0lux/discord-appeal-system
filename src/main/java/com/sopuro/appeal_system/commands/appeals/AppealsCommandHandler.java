package com.sopuro.appeal_system.commands.appeals;

import com.sopuro.appeal_system.commands.SlashCommand;
import com.sopuro.appeal_system.configs.AppealSystemConfig;
import com.sopuro.appeal_system.entities.GuildConfigEntity;
import com.sopuro.appeal_system.exceptions.MissingGuildContextException;
import com.sopuro.appeal_system.exceptions.NotAppealGuildException;
import com.sopuro.appeal_system.repositories.GuildConfigRepository;
import com.sopuro.appeal_system.shared.enums.AppealRole;
import com.sopuro.appeal_system.shared.enums.GuildConfig;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AppealsCommandHandler implements SlashCommand {
    private final AppealSystemConfig appealSystemConfig;
    private final GuildConfigRepository guildConfigRepository;

    @Override
    public String getName() {
        return "appeals";
    }

    @Override
    public List<AppealRole> allowedRoles() {
        return List.of(AppealRole.OVERSEER);
    }

    @Override
    public Mono<Void> preCondition(ChatInputInteractionEvent event) {
        String guildId = event.getInteraction()
                .getGuildId()
                .orElseThrow(MissingGuildContextException::new)
                .asString();

        if (!appealSystemConfig.isAppealServer(guildId))
            return Mono.error(new NotAppealGuildException(guildId));

        return Mono.empty();
    }

    @Override
    public Mono<Void> handle(ChatInputInteractionEvent event) {
        String guildId = event.getInteraction()
                .getGuildId()
                .orElseThrow(MissingGuildContextException::new)
                .asString();

        Boolean appealEnabled = event.getOption("accepting")
                .flatMap(option -> option.getOption("boolean"))
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asBoolean)
                .orElseThrow(() -> new IllegalStateException("Option 'accepting' is required"));

        return event.deferReply().withEphemeral(true)
                .then(Mono.defer(() -> {
                    GuildConfigEntity guildConfig = GuildConfigEntity.builder()
                            .guildId(guildId)
                            .configKey(GuildConfig.APPEAL_ENABLED)
                            .configValue(appealEnabled.toString())
                            .build();

                    return Mono.just(guildConfigRepository.save(guildConfig));
                }))
                .flatMap(config -> event
                        .editReply("Appeal system has been " + (appealEnabled ? "enabled" : "disabled") + ".")
                        .then());
    }
}
