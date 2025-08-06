package com.sopuro.appeal_system.commands.setup;

import com.sopuro.appeal_system.commands.SlashCommand;
import com.sopuro.appeal_system.components.messages.GenericSuccessFollowUp;
import com.sopuro.appeal_system.configs.AppealSystemConfig;
import com.sopuro.appeal_system.entities.GuildConfigEntity;
import com.sopuro.appeal_system.exceptions.appeal.CategoryAlreadySetupException;
import com.sopuro.appeal_system.exceptions.appeal.NotAppealGuildException;
import com.sopuro.appeal_system.repositories.GuildConfigRepository;
import com.sopuro.appeal_system.shared.enums.AppealRole;
import com.sopuro.appeal_system.shared.enums.GuildConfig;
import com.sopuro.appeal_system.shared.permissions.RoleOverwrites;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.PermissionOverwrite;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Role;
import discord4j.core.object.entity.channel.Category;
import discord4j.core.spec.CategoryCreateSpec;
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
    private static final String CATEGORY_OPEN_APPEALS = "Open Appeals";
    private static final String CATEGORY_CLOSED_APPEALS = "Closed Appeals";
    private final AppealSystemConfig appealSystemConfig;
    private final GuildConfigRepository guildConfigRepository;

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

        // Check if categories are already set up
        return event.getInteraction()
                .getGuild()
                .flatMap(guild -> guild.getChannels()
                        .ofType(Category.class)
                        .filter(category -> category.getName().equals(CATEGORY_OPEN_APPEALS)
                                || category.getName().equals(CATEGORY_CLOSED_APPEALS))
                        .collectList())
                .flatMap(categories -> {
                    if (!categories.isEmpty()) return Mono.error(new CategoryAlreadySetupException());
                    return Mono.empty();
                });
    }

    @Override
    public Mono<Void> handle(ChatInputInteractionEvent event) {
        return event.getInteraction().getGuild().flatMap(guild -> Mono.when(
                        createAppealCategory(guild, CATEGORY_OPEN_APPEALS),
                        createAppealCategory(guild, CATEGORY_CLOSED_APPEALS))
                .then(event.createFollowup(GenericSuccessFollowUp.create("Server has been successfully configured")))
                .then());
    }

    private Mono<Void> createAppealCategory(Guild guild, String categoryName) {
        Mono<Role> everyoneRole = guild.getEveryoneRole();
        Snowflake judgeRoleId =
                Snowflake.of(appealSystemConfig.getJudgeRoleId(guild.getId().asString()));
        Snowflake overseerRoleId =
                Snowflake.of(appealSystemConfig.getOverseerRoleId(guild.getId().asString()));

        boolean isOpenCategory = CATEGORY_OPEN_APPEALS.equals(categoryName);

        return everyoneRole.flatMap(everyone -> guild.createCategory(CategoryCreateSpec.builder()
                        .name(categoryName)
                        .addAllPermissionOverwrites(List.of(
                                PermissionOverwrite.forRole(
                                        everyone.getId(), PermissionSet.none(), PermissionSet.all()),
                                PermissionOverwrite.forRole(
                                        judgeRoleId,
                                        isOpenCategory
                                                ? RoleOverwrites.Judge.OPEN_PERMISSIONS
                                                : RoleOverwrites.Judge.CLOSED_PERMISSIONS,
                                        PermissionSet.none()),
                                PermissionOverwrite.forRole(
                                        overseerRoleId,
                                        isOpenCategory
                                                ? RoleOverwrites.Overseer.OPEN_PERMISSIONS
                                                : RoleOverwrites.Overseer.CLOSED_PERMISSIONS,
                                        PermissionSet.none())))
                        .build())
                .publishOn(Schedulers.boundedElastic())
                .map(category -> {
                    GuildConfigEntity config = GuildConfigEntity.builder()
                            .guildId(category.getGuildId().asString())
                            .configKey(
                                    isOpenCategory
                                            ? GuildConfig.OPEN_APPEALS_CATEGORY_ID
                                            : GuildConfig.CLOSED_APPEALS_CATEGORY_ID)
                            .configValue(category.getId().asString())
                            .build();

                    return guildConfigRepository.save(config);
                })
                .then());
    }
}
