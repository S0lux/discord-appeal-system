package com.sopuro.appeal_system.listeners.crossroads;

import com.sopuro.appeal_system.clients.opencloud.OpenCloudClient;
import com.sopuro.appeal_system.clients.opencloud.dtos.RobloxAvatarDto;
import com.sopuro.appeal_system.clients.opencloud.dtos.RobloxProfileDto;
import com.sopuro.appeal_system.clients.rover.RoverClient;
import com.sopuro.appeal_system.components.messages.CaseHistoryMessage;
import com.sopuro.appeal_system.components.messages.CaseInfoMessage;
import com.sopuro.appeal_system.components.messages.RobloxProfileMessage;
import com.sopuro.appeal_system.components.modals.ModalAppealDiscord;
import com.sopuro.appeal_system.configs.AppealSystemConfig;
import com.sopuro.appeal_system.dtos.GameConfigDto;
import com.sopuro.appeal_system.entities.CaseEntity;
import com.sopuro.appeal_system.entities.GuildConfigEntity;
import com.sopuro.appeal_system.exceptions.AppealException;
import com.sopuro.appeal_system.exceptions.appeal.ExistingPendingCaseException;
import com.sopuro.appeal_system.exceptions.appeal.IncorrectServerSetupException;
import com.sopuro.appeal_system.exceptions.appeal.MissingGuildContextException;
import com.sopuro.appeal_system.exceptions.rover.RobloxAccountNotVerifiedException;
import com.sopuro.appeal_system.repositories.CaseRepository;
import com.sopuro.appeal_system.repositories.GuildConfigRepository;
import com.sopuro.appeal_system.shared.enums.*;
import com.sopuro.appeal_system.shared.permissions.RoleOverwrites;
import com.sopuro.appeal_system.shared.utils.TokenHelper;
import discord4j.common.util.Snowflake;
import discord4j.common.util.TimestampFormat;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent;
import discord4j.core.object.PermissionOverwrite;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.Channel;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.spec.TextChannelCreateSpec;
import discord4j.rest.util.PermissionSet;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class CrossroadsModalListener {

    private static final Duration OPERATION_TIMEOUT = Duration.ofSeconds(30);
    private static final String CHANNEL_NAME_FORMAT = "%s-%s-%s";
    private static final String CHANNEL_TOPIC_FORMAT = "Appeal channel for **%s** (%s)\nAppealed at: %s";
    private static final String SUCCESS_MESSAGE_FORMAT =
            "<@%s> Your appeal has been successfully submitted! Please check the channel <#%s> for details.";
    private static final String ERROR_MESSAGE =
            "An error occurred while processing your command. Please try again later.";

    private final CaseRepository caseRepository;
    private final GuildConfigRepository guildConfigRepository;
    private final GatewayDiscordClient gatewayDiscordClient;
    private final AppealSystemConfig appealSystemConfig;
    private final RoverClient roverClient;
    private final OpenCloudClient openCloudClient;

    @PostConstruct
    public void initializeEventHandlers() {
        gatewayDiscordClient
                .on(ModalSubmitInteractionEvent.class)
                .filter(this::isDiscordAppealModal)
                .flatMap(this::processModalSubmission)
                .onErrorContinue(this::logUnexpectedError)
                .subscribe();
    }

    private boolean isDiscordAppealModal(ModalSubmitInteractionEvent event) {
        return event.getCustomId().startsWith(ModalAppealDiscord.DISCORD_MODAL_PREFIX);
    }

    private Mono<Void> processModalSubmission(ModalSubmitInteractionEvent event) {
        return handleDiscordModalSubmit(event)
                .onErrorResume(throwable -> handleModalSubmissionError(event, throwable))
                .timeout(OPERATION_TIMEOUT)
                .onErrorResume(throwable -> handleTimeout(event, throwable));
    }

    private Mono<Void> handleDiscordModalSubmit(ModalSubmitInteractionEvent event) {
        final Instant submittedAt = Instant.now();
        final AppealSubmissionContext context = createSubmissionContext(event, submittedAt);

        logAppealSubmission(context);

        return event.deferReply()
                .withEphemeral(true)
                .then(validateSubmission(context))
                .then(processAppealSubmission(event, context))
                .flatMap(channelId -> sendSuccessResponse(event, context.discordUserId(), channelId))
                .doOnSuccess(unused -> logSuccessfulSubmission(context, submittedAt));
    }

    private AppealSubmissionContext createSubmissionContext(ModalSubmitInteractionEvent event, Instant submittedAt) {
        final User user = event.getInteraction().getUser();
        final String guildId = event.getInteraction()
                .getGuildId()
                .orElseThrow(MissingGuildContextException::new)
                .asString();
        final String customId = event.getCustomId();

        return new AppealSubmissionContext(
                guildId,
                user.getId().asString(),
                user.getUsername(),
                appealSystemConfig.getGameConfigByServerId(guildId).normalizedName(),
                ModalAppealDiscord.getPunishmentTypeFromModalId(customId),
                submittedAt);
    }

    private void logAppealSubmission(AppealSubmissionContext context) {
        log.info(
                "Processing appeal submission - Game: '{}', User: '{}' ({}), Punishment: '{}'",
                context.normalizedGameName(),
                context.username(),
                context.discordUserId(),
                context.punishmentType());
    }

    private Mono<Void> validateSubmission(AppealSubmissionContext context) {
        return checkForExistingPendingCases(context);
    }

    private Mono<Snowflake> processAppealSubmission(
            ModalSubmitInteractionEvent event, AppealSubmissionContext context) {
        return retrieveUserRobloxAccount(context)
                .flatMap(robloxProfile -> createAppealChannelAndCase(event, context, robloxProfile))
                .flatMap(tuple -> populateChannelWithDetails(event, tuple.getT1(), tuple.getT2(), tuple.getT3()))
                .map(Tuple2::getT1);
    }

    private Mono<Void> checkForExistingPendingCases(AppealSubmissionContext context) {
        return Mono.fromCallable(() -> caseRepository.findAppealCase(
                        context.normalizedGameName(),
                        context.punishmentType(),
                        context.discordUserId(),
                        AppealVerdict.PENDING,
                        AppealPlatform.DISCORD))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(existingCase -> {
                    if (existingCase.isPresent()) {
                        log.info(
                                "Rejecting duplicate appeal - User: {}, Game: '{}', Punishment: '{}'",
                                context.discordUserId(),
                                context.normalizedGameName(),
                                context.punishmentType());
                        return Mono.error(new ExistingPendingCaseException());
                    }
                    return Mono.empty();
                });
    }

    private Mono<RobloxProfileDto> retrieveUserRobloxAccount(AppealSubmissionContext context) {
        final String roverToken =
                TokenHelper.retrieveRoverTokenForGame(context.normalizedGameName(), ServerType.APPEAL);

        return Mono.fromCallable(() -> roverClient.toRoblox(context.guildId(), context.discordUserId(), roverToken))
                .subscribeOn(Schedulers.boundedElastic())
                .map(robloxAccount -> String.valueOf(robloxAccount.robloxId()))
                .flatMap(this::fetchRobloxProfile)
                .onErrorMap(HttpClientErrorException.NotFound.class, ex -> new RobloxAccountNotVerifiedException());
    }

    private Mono<RobloxProfileDto> fetchRobloxProfile(String robloxId) {
        return Mono.fromCallable(() -> openCloudClient.getRobloxProfile(robloxId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<Tuple3<Snowflake, RobloxProfileDto, CaseEntity>> createAppealChannelAndCase(
            ModalSubmitInteractionEvent event,
            AppealSubmissionContext context,
            RobloxProfileDto robloxProfile) {

        return createAppealChannel(context, robloxProfile.name())
                .flatMap(channelId -> persistAppealCase(event, context, robloxProfile.id(), channelId)
                        .map(caseId -> Tuples.of(channelId, robloxProfile, caseId)));
    }

    private Mono<Tuple2<Snowflake, RobloxProfileDto>> populateChannelWithDetails(
            ModalSubmitInteractionEvent event,
            Snowflake channelId,
            RobloxProfileDto robloxProfile,
            CaseEntity caseEntity) {

        return sendCaseDetailsMessage(channelId, event, caseEntity)
                .then(fetchAndSendRobloxProfileMessage(channelId, robloxProfile))
                .then(sendCaseHistoryMessage(
                        channelId,
                        event.getInteraction().getUser().getId().asString(),
                        robloxProfile.id(),
                        robloxProfile.name()))
                .thenReturn(Tuples.of(channelId, robloxProfile));
    }

    private Mono<Snowflake> createAppealChannel(AppealSubmissionContext context, String robloxUsername) {
        final String channelName = generateChannelName(context, robloxUsername);

        return Mono.fromCallable(() -> guildConfigRepository.findByGuildIdAndConfigKey(
                        context.guildId(), GuildConfig.OPEN_APPEALS_CATEGORY_ID))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(this::validateGuildConfig)
                .flatMap(categoryId -> createTextChannelWithPermissions(context, channelName, categoryId));
    }

    private String generateChannelName(AppealSubmissionContext context, String robloxUsername) {
        final String username =
                (robloxUsername != null && !robloxUsername.isBlank()) ? robloxUsername : context.username();

        return String.format(
                CHANNEL_NAME_FORMAT,
                AppealPlatform.DISCORD.name().toLowerCase(),
                context.punishmentType().name().toLowerCase(),
                username);
    }

    private Mono<String> validateGuildConfig(Optional<GuildConfigEntity> guildConfigOpt) {
        return guildConfigOpt
                .map(o -> Mono.just(o.getConfigValue()))
                .orElseGet(() -> Mono.error(new IncorrectServerSetupException()));
    }

    private Mono<Snowflake> createTextChannelWithPermissions(
            AppealSubmissionContext context, String channelName, String categoryId) {

        final TextChannelCreateSpec channelSpec = buildChannelCreateSpec(context, channelName, categoryId);

        return gatewayDiscordClient
                .getGuildById(Snowflake.of(context.guildId()))
                .flatMap(guild -> guild.createTextChannel(channelSpec))
                .map(Channel::getId);
    }

    private TextChannelCreateSpec buildChannelCreateSpec(
            AppealSubmissionContext context, String channelName, String categoryId) {

        final GameConfigDto gameConfig = appealSystemConfig.getGameConfigByServerId(context.guildId());
        final String topic = String.format(
                CHANNEL_TOPIC_FORMAT,
                context.username(),
                context.discordUserId(),
                TimestampFormat.LONG_DATE_TIME.format(context.submittedAt()));

        return TextChannelCreateSpec.builder()
                .name(channelName)
                .parentId(Snowflake.of(categoryId))
                .topic(topic)
                .addAllPermissionOverwrites(
                        createPermissionOverwrites(context.guildId(), context.discordUserId(), gameConfig))
                .build();
    }

    private List<PermissionOverwrite> createPermissionOverwrites(
            String guildId, String userId, GameConfigDto gameConfig) {
        return List.of(
                PermissionOverwrite.forRole(Snowflake.of(guildId), PermissionSet.none(), PermissionSet.all()),
                PermissionOverwrite.forMember(
                        Snowflake.of(userId), RoleOverwrites.Member.OPEN_PERMISSIONS, PermissionSet.none()),
                PermissionOverwrite.forRole(
                        Snowflake.of(gameConfig.appealJudgeRoleId()),
                        RoleOverwrites.Judge.OPEN_PERMISSIONS,
                        PermissionSet.none()),
                PermissionOverwrite.forRole(
                        Snowflake.of(gameConfig.appealOverseerRoleId()),
                        RoleOverwrites.Overseer.OPEN_PERMISSIONS,
                        PermissionSet.none()));
    }

    private Mono<Void> sendCaseDetailsMessage(Snowflake channelId, ModalSubmitInteractionEvent event, CaseEntity caseEntity) {
        String gameName = appealSystemConfig
                .getGameConfigByServerId(event.getInteraction().getGuildId().orElseThrow(MissingGuildContextException::new).asString())
                .normalizedName();
        return getTextChannel(channelId)
                .flatMap(channel -> channel.createMessage(CaseInfoMessage.create(caseEntity)))
                .then();
    }

    private Mono<Void> fetchAndSendRobloxProfileMessage(Snowflake channelId, RobloxProfileDto robloxProfile) {
        return Mono.fromCallable(() -> openCloudClient.getRobloxAvatar(robloxProfile.id()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(avatar -> sendRobloxProfileMessage(channelId, robloxProfile, avatar));
    }

    private Mono<Void> sendRobloxProfileMessage(
            Snowflake channelId, RobloxProfileDto profile, RobloxAvatarDto avatar) {

        return getTextChannel(channelId)
                .flatMap(channel -> channel.createMessage(RobloxProfileMessage.create(profile, avatar)))
                .then();
    }

    private Mono<Void> sendCaseHistoryMessage(
            Snowflake channelId, String discordUserId, String robloxId, String username) {

        return Mono.fromCallable(() -> caseRepository.getCasesOfAppealer(discordUserId, robloxId))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(cases -> getTextChannel(channelId)
                        .flatMap(
                                channel -> channel.createMessage(CaseHistoryMessage.create(cases, robloxId, username))))
                .then();
    }

    private Mono<TextChannel> getTextChannel(Snowflake channelId) {
        return gatewayDiscordClient.getChannelById(channelId).cast(TextChannel.class);
    }

    private Mono<CaseEntity> persistAppealCase(
            ModalSubmitInteractionEvent event, AppealSubmissionContext context, String robloxId, Snowflake channelId) {

        final CaseEntity caseEntity = buildCaseEntity(event, context, robloxId, channelId.asString());

        return Mono.fromCallable(() -> caseRepository.save(caseEntity))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private CaseEntity buildCaseEntity(
            ModalSubmitInteractionEvent event, AppealSubmissionContext context, String robloxId, String channelId) {

        final CaseEntity caseEntity = new CaseEntity();
        caseEntity.setAppealerDiscordId(context.discordUserId());
        caseEntity.setAppealerRobloxId(robloxId);
        caseEntity.setChannelId(channelId);
        caseEntity.setPunishmentType(context.punishmentType());
        caseEntity.setPunishmentReason(ModalAppealDiscord.getPunishmentReason(event));
        caseEntity.setGame(context.normalizedGameName());
        caseEntity.setAppealReason(ModalAppealDiscord.getAppealReason(event));
        caseEntity.setAppealVerdict(AppealVerdict.PENDING);
        caseEntity.setAppealPlatform(AppealPlatform.DISCORD);
        caseEntity.setAppealedAt(context.submittedAt());

        return caseEntity;
    }

    private Mono<Void> sendSuccessResponse(ModalSubmitInteractionEvent event, String userId, Snowflake channelId) {
        final String message = String.format(SUCCESS_MESSAGE_FORMAT, userId, channelId.asString());
        return event.editReply(message).then();
    }

    private void logSuccessfulSubmission(AppealSubmissionContext context, Instant startTime) {
        final long duration = Instant.now().toEpochMilli() - startTime.toEpochMilli();
        log.info(
                "Appeal channel created successfully - User: {} ({}), Game: '{}', Duration: {}ms",
                context.username(),
                context.discordUserId(),
                context.normalizedGameName(),
                duration);
    }

    private Mono<Void> handleModalSubmissionError(ModalSubmitInteractionEvent event, Throwable error) {
        if (error instanceof AppealException) {
            log.warn("Appeal processing failed: {}", error.getMessage());
            return event.editReply(error.getMessage()).then();
        }

        log.error("Unexpected error during appeal processing", error);
        return event.editReply(ERROR_MESSAGE).then();
    }

    private Mono<Void> handleTimeout(ModalSubmitInteractionEvent event, Throwable error) {
        log.error(
                "Appeal processing timed out for user: {}",
                event.getInteraction().getUser().getUsername(),
                error);
        return event.editReply("The request timed out. Please try again.").then();
    }

    private void logUnexpectedError(Throwable error, Object trigger) {
        log.error("Unexpected error in modal listener", error);
    }

    private record AppealSubmissionContext(
            String guildId,
            String discordUserId,
            String username,
            String normalizedGameName,
            PunishmentType punishmentType,
            Instant submittedAt) {}
}