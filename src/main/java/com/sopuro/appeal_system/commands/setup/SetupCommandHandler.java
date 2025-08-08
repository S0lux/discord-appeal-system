package com.sopuro.appeal_system.commands.setup;

import com.sopuro.appeal_system.commands.SlashCommand;
import com.sopuro.appeal_system.components.messages.GenericSuccessFollowUp;
import com.sopuro.appeal_system.configs.AppealSystemConfig;
import com.sopuro.appeal_system.entities.GuildConfigEntity;
import com.sopuro.appeal_system.exceptions.appeal.NotAppealGuildException;
import com.sopuro.appeal_system.repositories.GuildConfigRepository;
import com.sopuro.appeal_system.shared.enums.AppealRole;
import com.sopuro.appeal_system.shared.enums.GuildConfig;
import com.sopuro.appeal_system.shared.permissions.RoleOverwrites;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.PermissionOverwrite;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Role;
import discord4j.core.object.entity.channel.Category;
import discord4j.core.retriever.EntityRetrievalStrategy;
import discord4j.core.spec.CategoryCreateSpec;
import discord4j.rest.http.client.ClientException;
import discord4j.rest.util.PermissionSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SetupCommandHandler implements SlashCommand {
    private static final String COMMAND_NAME = "setup";
    private static final String CATEGORY_OPEN_DISCORD_APPEALS_NAME = "Discord Appeals";
    private static final String CATEGORY_OPEN_GAME_APPEALS_NAME = "Game Appeals";
    private static final String CATEGORY_CLOSED_APPEALS_NAME = "Closed Appeals";
    private final AppealSystemConfig appealSystemConfig;
    private final GuildConfigRepository guildConfigRepository;
    private final GatewayDiscordClient gateway;

    @Override
    public String getName() {
        return COMMAND_NAME;
    }

    @Override
    public List<AppealRole> allowedRoles() {
        return List.of(AppealRole.OVERSEER);
    }

    @Override
    public Mono<Void> preCondition(ChatInputInteractionEvent event) {
        // Check if the command is executed in an APPEAL guild
        String guildId = event.getInteraction().getGuildId().get().asString();
        if (!appealSystemConfig.isAppealServer(guildId)) return Mono.error(new NotAppealGuildException(guildId));

        return Mono.empty();
    }

    @Override
    public Mono<Void> handle(ChatInputInteractionEvent event) {
        return event.getInteraction().getGuild().flatMap(guild -> Mono.when(
                        createAppealCategory(guild, GuildConfig.OPEN_APPEALS_DISCORD_CATEGORY_ID),
                        createAppealCategory(guild, GuildConfig.OPEN_APPEALS_GAME_CATEGORY_ID),
                        createAppealCategory(guild, GuildConfig.CLOSED_APPEALS_CATEGORY_ID))
                .then(event.createFollowup(GenericSuccessFollowUp.create("Server has been successfully configured")))
                .then());
    }

    private Mono<Boolean> checkCategoryExistAndValid(Guild guild, GuildConfig config) {
        return Mono.fromCallable(() -> guildConfigRepository.findByGuildIdAndConfigKey(
                        guild.getId().asString(), config))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(optConfig -> {
                    if (optConfig.isEmpty()) return Mono.just(false);

                    return gateway
                            .withRetrievalStrategy(EntityRetrievalStrategy.REST)
                            .getChannelById(Snowflake.of(optConfig.get().getConfigValue()))
                            .flatMap(channel -> {
                                log.info("Found channel {}", channel.getId());
                                return Mono.just(channel instanceof Category);
                            })
                            .onErrorResume(ClientException.isStatusCode(404), ignored -> Mono.just(false));
                });
    }

    private Mono<Void> createAppealCategory(Guild guild, GuildConfig categoryType) {
        Snowflake judgeRoleId =
                Snowflake.of(appealSystemConfig.getJudgeRoleId(guild.getId().asString()));
        Snowflake overseerRoleId =
                Snowflake.of(appealSystemConfig.getOverseerRoleId(guild.getId().asString()));

        return checkCategoryExistAndValid(guild, categoryType).flatMap(isValid -> {
            // Valid, no need to do anything
            log.info("Is valid: {}", isValid);
            if (isValid) return Mono.empty();

            // Create the new category and save to DB
            String categoryName =
                    switch (categoryType) {
                        case OPEN_APPEALS_DISCORD_CATEGORY_ID -> CATEGORY_OPEN_DISCORD_APPEALS_NAME;
                        case OPEN_APPEALS_GAME_CATEGORY_ID -> CATEGORY_OPEN_GAME_APPEALS_NAME;
                        case CLOSED_APPEALS_CATEGORY_ID -> CATEGORY_CLOSED_APPEALS_NAME;
                        default -> "Unknown";
                    };

            boolean isClosedCategory = categoryType == GuildConfig.CLOSED_APPEALS_CATEGORY_ID;

            return guild.createCategory(CategoryCreateSpec.builder()
                            .name(categoryName)
                            .addAllPermissionOverwrites(List.of(
                                    PermissionOverwrite.forRole(
                                            guild.getId(), PermissionSet.none(), PermissionSet.all()),
                                    PermissionOverwrite.forRole(
                                            judgeRoleId,
                                            isClosedCategory
                                                    ? RoleOverwrites.Judge.CLOSED_PERMISSIONS
                                                    : RoleOverwrites.Judge.OPEN_PERMISSIONS,
                                            PermissionSet.none()),
                                    PermissionOverwrite.forRole(
                                            overseerRoleId,
                                            isClosedCategory
                                                    ? RoleOverwrites.Overseer.CLOSED_PERMISSIONS
                                                    : RoleOverwrites.Overseer.OPEN_PERMISSIONS,
                                            PermissionSet.none())))
                            .build())
                    // Save to DB
                    .flatMap(category -> Mono.fromCallable(() -> {
                                GuildConfigEntity configEntity = GuildConfigEntity.builder()
                                        .guildId(guild.getId().asString())
                                        .configKey(categoryType)
                                        .configValue(category.getId().asString())
                                        .build();

                                return guildConfigRepository.save(configEntity);
                            })
                            .subscribeOn(Schedulers.boundedElastic())
                            .then());
        });
    }
}
