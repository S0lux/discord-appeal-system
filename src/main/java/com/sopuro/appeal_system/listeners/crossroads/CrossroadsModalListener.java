package com.sopuro.appeal_system.listeners.crossroads;

import com.sopuro.appeal_system.clients.opencloud.OpenCloudClient;
import com.sopuro.appeal_system.clients.opencloud.dtos.OpenCloudRobloxAvatarDto;
import com.sopuro.appeal_system.clients.opencloud.dtos.OpenCloudRobloxProfileDto;
import com.sopuro.appeal_system.clients.rover.RoverClient;
import com.sopuro.appeal_system.components.messages.CaseHistoryMessage;
import com.sopuro.appeal_system.components.messages.CaseInfoMessage;
import com.sopuro.appeal_system.components.messages.RobloxProfileMessage;
import com.sopuro.appeal_system.components.modals.ModalAppealDiscord;
import com.sopuro.appeal_system.configs.AppealSystemConfig;
import com.sopuro.appeal_system.dtos.GameConfigDto;
import com.sopuro.appeal_system.entities.CaseEntity;
import com.sopuro.appeal_system.exceptions.AppealException;
import com.sopuro.appeal_system.exceptions.appeal.ExistingPendingCaseException;
import com.sopuro.appeal_system.exceptions.appeal.IncorrectServerSetupException;
import com.sopuro.appeal_system.exceptions.appeal.MissingGuildContextException;
import com.sopuro.appeal_system.exceptions.rover.RobloxAccountNotVerifiedException;
import com.sopuro.appeal_system.repositories.CaseRepository;
import com.sopuro.appeal_system.repositories.GuildConfigRepository;
import com.sopuro.appeal_system.shared.enums.AppealPlatform;
import com.sopuro.appeal_system.shared.enums.AppealVerdict;
import com.sopuro.appeal_system.shared.enums.GuildConfig;
import com.sopuro.appeal_system.shared.enums.PunishmentType;
import com.sopuro.appeal_system.shared.permissions.RoleOverwrites;
import com.sopuro.appeal_system.shared.utils.TokenHelper;
import discord4j.common.util.Snowflake;
import discord4j.common.util.TimestampFormat;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent;
import discord4j.core.object.PermissionOverwrite;
import discord4j.core.object.entity.channel.Channel;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.spec.TextChannelCreateSpec;
import discord4j.rest.util.PermissionSet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@Slf4j
public class CrossroadsModalListener {
    private final CaseRepository caseRepository;
    private final GuildConfigRepository guildConfigRepository;
    private final GatewayDiscordClient gatewayDiscordClient;
    private final AppealSystemConfig appealSystemConfig;
    private final RoverClient roverClient;
    private final OpenCloudClient openCloudClient;

    public CrossroadsModalListener(
            CaseRepository caseRepository,
            GuildConfigRepository guildConfigRepository,
            GatewayDiscordClient gatewayDiscordClient,
            AppealSystemConfig appealSystemConfig,
            RoverClient roverClient,
            OpenCloudClient openCloudClient) {
        this.caseRepository = caseRepository;
        this.guildConfigRepository = guildConfigRepository;
        this.gatewayDiscordClient = gatewayDiscordClient;
        this.appealSystemConfig = appealSystemConfig;
        this.roverClient = roverClient;
        this.openCloudClient = openCloudClient;

        gatewayDiscordClient
                .on(ModalSubmitInteractionEvent.class)
                .filter(event -> event.getCustomId().startsWith(ModalAppealDiscord.DISCORD_MODAL_PREFIX))
                .flatMap(event -> this.handleDiscordModalSubmit(event)
                        .onErrorResume(throwable -> handleCommandError(event, throwable)))
                .subscribe();
    }

    private Mono<Void> handleDiscordModalSubmit(ModalSubmitInteractionEvent event) {
        Instant submittedAt = Instant.now();

        String guildId = event.getInteraction()
                .getGuildId()
                .orElseThrow(MissingGuildContextException::new)
                .asString();
        String discordUserId = event.getInteraction().getUser().getId().asString();

        String normalizedGameName = ModalAppealDiscord.getNormalizedGameNameFromModalId(event.getCustomId());
        PunishmentType punishmentType = ModalAppealDiscord.getPunishmentTypeFromModalId(event.getCustomId());

        return event.deferReply()
                .withEphemeral(true)
                .then(checkExistingPendingCases(normalizedGameName, punishmentType, discordUserId))
                .then(getUserRobloxAccount(guildId, discordUserId, normalizedGameName))
                .flatMap(robloxAccount -> createCaseChannel(event, punishmentType, robloxAccount.name(), submittedAt)
                        .zipWith(Mono.just(robloxAccount)))
                .flatMap(tuple -> {
                    Snowflake channelId = tuple.getT1();
                    OpenCloudRobloxProfileDto robloxAccount = tuple.getT2();

                    Mono<OpenCloudRobloxAvatarDto> robloxAvatarMono = Mono.fromCallable(
                                    () -> openCloudClient.getRobloxAvatar(robloxAccount.id()))
                            .subscribeOn(Schedulers.boundedElastic());

                    return persistCase(event, discordUserId, robloxAccount.id(), channelId.asString(), submittedAt)
                            .flatMap(caseId -> sendCaseDetailsMessage(channelId, event, caseId)
                                    .then(robloxAvatarMono)
                                    .flatMap(avatarDto -> sendRobloxProfileMessage(channelId, robloxAccount, avatarDto))
                                    .then(sendCaseHistoryMessage(
                                            channelId, discordUserId, robloxAccount.id(), robloxAccount.name()))
                                    .thenReturn(channelId));
                })
                .flatMap(channelId ->
                        event.editReply("<@" + discordUserId + ">" + "Your appeal has been successfully submitted! "
                                + "Please check the channel <#" + channelId.asString() + "> for details."))
                .then();
    }

    private Mono<Void> checkExistingPendingCases(String gameName, PunishmentType punishmentType, String discordUserId) {
        Mono<Optional<CaseEntity>> existingCaseMono = Mono.fromCallable(() -> caseRepository.findAppealCase(
                        gameName, punishmentType, discordUserId, AppealVerdict.PENDING, AppealPlatform.DISCORD))
                .subscribeOn(Schedulers.boundedElastic());

        return existingCaseMono.flatMap(existingCase -> {
            if (existingCase.isPresent()) {
                return Mono.error(new ExistingPendingCaseException());
            }
            return Mono.empty();
        });
    }

    private Mono<OpenCloudRobloxProfileDto> getUserRobloxAccount(
            String guildId, String discordUserId, String gameName) {
        String roverToken = TokenHelper.retrieveRoverTokenForGame(gameName);
        return Mono.fromCallable(() -> roverClient.toRoblox(guildId, discordUserId, roverToken))
                .subscribeOn(Schedulers.boundedElastic())
                .map(robloxAccount -> String.valueOf(robloxAccount.robloxId()))
                .flatMap(robloxId -> Mono.fromCallable(() -> openCloudClient.getRobloxProfile(robloxId))
                        .subscribeOn(Schedulers.boundedElastic()))
                .onErrorMap(throwable -> {
                    if (throwable instanceof HttpClientErrorException.NotFound)
                        return new RobloxAccountNotVerifiedException();
                    return throwable;
                });
    }

    private TextChannelCreateSpec generateCaseChannelPermissionSpec(
            String everyoneRoleId,
            String channelName,
            String categoryId,
            String appealerId,
            String appealerUsername,
            Instant appealedAt) {
        // Everyone role ID is the same as the guild ID
        GameConfigDto gameConfigDto = appealSystemConfig.getGameConfigByServerId(everyoneRoleId);
        Snowflake judgeRoleId = Snowflake.of(gameConfigDto.appealJudgeRoleId());
        Snowflake overseerRoleId = Snowflake.of(gameConfigDto.appealOverseerRoleId());

        return TextChannelCreateSpec.builder()
                .name(channelName)
                .parentId(Snowflake.of(categoryId))
                .topic(String.format(
                        "Appeal channel for **%s** (%s)\nAppealed at: %s",
                        appealerUsername, appealerId, TimestampFormat.LONG_DATE_TIME.format(appealedAt)))
                .addAllPermissionOverwrites(List.of(
                        PermissionOverwrite.forRole(
                                Snowflake.of(everyoneRoleId), PermissionSet.none(), PermissionSet.all()),
                        PermissionOverwrite.forMember(
                                Snowflake.of(appealerId), RoleOverwrites.Member.OPEN_PERMISSIONS, PermissionSet.none()),
                        PermissionOverwrite.forRole(
                                judgeRoleId, RoleOverwrites.Judge.OPEN_PERMISSIONS, PermissionSet.none()),
                        PermissionOverwrite.forRole(
                                overseerRoleId, RoleOverwrites.Overseer.OPEN_PERMISSIONS, PermissionSet.none())))
                .build();
    }

    private Mono<Snowflake> createCaseChannel(
            ModalSubmitInteractionEvent event,
            PunishmentType punishmentType,
            String usernameOverride,
            Instant submittedAt) {
        String username = event.getInteraction().getUser().getUsername();
        String userId = event.getInteraction().getUser().getId().asString();
        String channelName = String.format(
                "%s-%s-%s",
                AppealPlatform.DISCORD.name().toLowerCase(),
                punishmentType.name().toLowerCase(),
                usernameOverride != null && !usernameOverride.isBlank() ? usernameOverride : username);

        return event.getInteraction()
                .getGuildId()
                .map(Mono::just)
                .orElse(Mono.error(MissingGuildContextException::new))
                .flatMap(guildId -> Mono.fromCallable(() -> guildConfigRepository.findByGuildIdAndConfigKey(
                                guildId.asString(), GuildConfig.OPEN_APPEALS_CATEGORY_ID))
                        .subscribeOn(Schedulers.boundedElastic()))
                .flatMap(guildConfig -> {
                    if (guildConfig.isEmpty()) return Mono.error(new IncorrectServerSetupException());

                    Snowflake guildId = Snowflake.of(guildConfig.get().getGuildId());
                    String categoryId = guildConfig.get().getConfigValue();

                    return gatewayDiscordClient
                            .getGuildById(guildId)
                            .flatMap(guild -> guild.createTextChannel(generateCaseChannelPermissionSpec(
                                    guildId.asString(), channelName, categoryId, userId, username, submittedAt)))
                            .map(Channel::getId);
                });
    }

    private Mono<Snowflake> sendCaseDetailsMessage(
            Snowflake channelId, ModalSubmitInteractionEvent event, UUID caseId) {
        return gatewayDiscordClient
                .getChannelById(channelId)
                .cast(TextChannel.class)
                .flatMap(channel -> channel.createMessage(CaseInfoMessage.create(event, caseId.toString())))
                .thenReturn(channelId);
    }

    private Mono<Snowflake> sendRobloxProfileMessage(
            Snowflake channelId, OpenCloudRobloxProfileDto robloxProfileDto, OpenCloudRobloxAvatarDto robloxAvatarDto) {
        return gatewayDiscordClient
                .getChannelById(channelId)
                .cast(TextChannel.class)
                .flatMap(channel ->
                        channel.createMessage(RobloxProfileMessage.create(robloxProfileDto, robloxAvatarDto)))
                .thenReturn(channelId);
    }

    private Mono<List<CaseEntity>> getCasesOfAppealer(String discordUserId, String robloxId) {
        return Mono.fromCallable(() -> caseRepository.getCasesOfAppealer(discordUserId, robloxId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<Snowflake> sendCaseHistoryMessage(
            Snowflake channelId, String appealerDiscordId, String appealerRobloxId, String appealerUsername) {
        return getCasesOfAppealer(appealerDiscordId, appealerRobloxId)
                .flatMap(caseEntities -> gatewayDiscordClient
                        .getChannelById(channelId)
                        .cast(TextChannel.class)
                        .flatMap(channel -> channel.createMessage(
                                CaseHistoryMessage.create(caseEntities, appealerRobloxId, appealerUsername))))
                .thenReturn(channelId);
    }

    private Mono<UUID> persistCase(
            ModalSubmitInteractionEvent event,
            String discordUserId,
            String robloxId,
            String channelId,
            Instant submittedAt) {

        PunishmentType punishmentType = ModalAppealDiscord.getPunishmentTypeFromModalId(event.getCustomId());
        String gameName = ModalAppealDiscord.getNormalizedGameNameFromModalId(event.getCustomId());
        String appealReason = ModalAppealDiscord.getAppealReason(event);
        String punishmentReason = ModalAppealDiscord.getPunishmentReason(event);

        CaseEntity caseEntity = new CaseEntity();
        caseEntity.setAppealerDiscordId(discordUserId);
        caseEntity.setAppealerRobloxId(robloxId);
        caseEntity.setChannelId(channelId);
        caseEntity.setPunishmentType(punishmentType);
        caseEntity.setPunishmentReason(punishmentReason);
        caseEntity.setGame(gameName);
        caseEntity.setAppealReason(appealReason);
        caseEntity.setAppealVerdict(AppealVerdict.PENDING);
        caseEntity.setAppealPlatform(AppealPlatform.DISCORD);
        caseEntity.setAppealedAt(submittedAt);

        return Mono.fromCallable(() -> caseRepository.save(caseEntity))
                .subscribeOn(Schedulers.boundedElastic())
                .map(CaseEntity::getId);
    }

    private Mono<Void> handleCommandError(ModalSubmitInteractionEvent event, Throwable error) {
        if (error instanceof AppealException)
            return event.editReply(error.getMessage()).then();

        log.error(error.getMessage(), error);
        return event.editReply("An error occurred while processing your command. Please try again later.")
                .then();
    }
}
