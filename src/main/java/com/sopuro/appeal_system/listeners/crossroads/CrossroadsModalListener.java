package com.sopuro.appeal_system.listeners.crossroads;

import com.sopuro.appeal_system.clients.opencloud.OpenCloudClient;
import com.sopuro.appeal_system.clients.opencloud.dtos.RobloxAvatarDto;
import com.sopuro.appeal_system.clients.opencloud.dtos.RobloxProfileDto;
import com.sopuro.appeal_system.clients.rover.RoverClient;
import com.sopuro.appeal_system.clients.rover.dtos.DiscordToRobloxDto;
import com.sopuro.appeal_system.components.messages.CaseHistoryMessage;
import com.sopuro.appeal_system.components.messages.CaseInfoMessage;
import com.sopuro.appeal_system.components.messages.GenericErrorFollowUp;
import com.sopuro.appeal_system.components.messages.RobloxProfileMessage;
import com.sopuro.appeal_system.components.modals.DiscordAppealModal;
import com.sopuro.appeal_system.components.modals.GameAppealModal;
import com.sopuro.appeal_system.configs.AppealSystemConfig;
import com.sopuro.appeal_system.dtos.GameConfigDto;
import com.sopuro.appeal_system.entities.CaseEntity;
import com.sopuro.appeal_system.entities.GuildConfigEntity;
import com.sopuro.appeal_system.exceptions.AppealException;
import com.sopuro.appeal_system.exceptions.appeal.ExistingPendingCaseException;
import com.sopuro.appeal_system.exceptions.appeal.IncorrectServerSetupException;
import com.sopuro.appeal_system.exceptions.appeal.UserIsNotRobloxBannedException;
import com.sopuro.appeal_system.exceptions.rover.RobloxAccountNotVerifiedException;
import com.sopuro.appeal_system.repositories.CaseRepository;
import com.sopuro.appeal_system.repositories.GuildConfigRepository;
import com.sopuro.appeal_system.services.AppealValidationService;
import com.sopuro.appeal_system.shared.AppealSubmissionContextFactory;
import com.sopuro.appeal_system.shared.enums.*;
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
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@Component
@Slf4j
@RequiredArgsConstructor
public class CrossroadsModalListener {

    private static final Duration OPERATION_TIMEOUT = Duration.ofSeconds(30);
    private static final String CHANNEL_NAME_FORMAT = "%s-%s-%s";
    private static final String CHANNEL_TOPIC_FORMAT = "Appeal channel for **%s** (%s)\nAppealed at: %s";
    private static final String SUCCESS_MESSAGE_FORMAT =
            "<@%s> Your appeal has been successfully submitted! Please check the channel <#%s> for details.";
    private static final String DEFAULT_ERROR_MESSAGE =
            "An error occurred while processing your command. Please try again later.";
    private static final String TIMEOUT_MESSAGE = "The request timed out. Please try again.";

    private final CaseRepository caseRepository;
    private final GuildConfigRepository guildConfigRepository;
    private final GatewayDiscordClient gatewayDiscordClient;
    private final AppealSystemConfig appealSystemConfig;
    private final RoverClient roverClient;
    private final OpenCloudClient openCloudClient;
    private final AppealValidationService validationService;
    private final AppealSubmissionContextFactory contextFactory;

    @PostConstruct
    public void initializeEventHandlers() {
        gatewayDiscordClient
                .on(ModalSubmitInteractionEvent.class)
                .filter(this::isDiscordAppealModal)
                .flatMap(this::processDiscordModalSubmission)
                .onErrorContinue(this::logUnexpectedError)
                .subscribe();

        gatewayDiscordClient
                .on(ModalSubmitInteractionEvent.class)
                .filter(this::isGameAppealModal)
                .flatMap(this::processGameModalSubmission)
                .onErrorContinue(this::logUnexpectedError)
                .subscribe();
    }

    private boolean isDiscordAppealModal(ModalSubmitInteractionEvent event) {
        return event.getCustomId().startsWith(DiscordAppealModal.INSTANCE.getModalPrefix());
    }

    private boolean isGameAppealModal(ModalSubmitInteractionEvent event) {
        return event.getCustomId().startsWith(GameAppealModal.INSTANCE.getModalPrefix());
    }

    private Mono<Void> processDiscordModalSubmission(ModalSubmitInteractionEvent event) {
        return processModalSubmission(event, context -> validationService.validateDiscordAppeal(event));
    }

    private Mono<Void> processGameModalSubmission(ModalSubmitInteractionEvent event) {
        return processModalSubmission(event, context -> validateGameAppeal(event, context));
    }

    private Mono<Void> processModalSubmission(
            ModalSubmitInteractionEvent event,
            Function<AppealSubmissionContextFactory.AppealSubmissionContext, Mono<Void>> specificValidator) {

        final Instant submittedAt = Instant.now();
        final AppealSubmissionContextFactory.AppealSubmissionContext context = contextFactory.createContext(event, submittedAt);

        logAppealSubmission(context);

        return event.deferReply()
                .withEphemeral(true)
                .then(specificValidator.apply(context))
                .then(checkForExistingPendingCases(context))
                .then(processAppealSubmission(event, context))
                .flatMap(channelId -> sendSuccessResponse(event, context.discordUserId(), channelId))
                .doOnSuccess(ignored -> logSuccessfulSubmission(context, submittedAt))
                .onErrorResume(throwable -> handleModalSubmissionError(event, throwable))
                .timeout(OPERATION_TIMEOUT)
                .onErrorResume(throwable -> handleTimeout(event, throwable));
    }

    private Mono<Void> validateGameAppeal(ModalSubmitInteractionEvent event, AppealSubmissionContextFactory.AppealSubmissionContext context) {
        return validationService.validateGameAppeal(event)
                .then(checkUserBannedInGame(context));
    }

    private Mono<Void> checkUserBannedInGame(AppealSubmissionContextFactory.AppealSubmissionContext context) {
        final GameConfigDto gameConfig = appealSystemConfig.getGameConfigByServerId(context.guildId());
        final String token = TokenHelper.retrieveRoverTokenForGame(gameConfig.normalizedName(), ServerType.APPEAL);

        return getRobloxAccountForUser(context.guildId(), context.discordUserId(), token)
                .flatMap(profile -> validateUserRestriction(gameConfig, profile))
                .onErrorMap(HttpClientErrorException.NotFound.class,
                        _ -> new UserIsNotRobloxBannedException(gameConfig.name()));
    }

    private Mono<DiscordToRobloxDto> getRobloxAccountForUser(String guildId, String userId, String token) {
        return Mono.fromCallable(() -> roverClient.toRoblox(guildId, userId, token))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<Void> validateUserRestriction(GameConfigDto gameConfig, DiscordToRobloxDto profile) {
        return Mono.fromCallable(() -> openCloudClient.getUserRestriction(
                        gameConfig.universeId(), String.valueOf(profile.robloxId())))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(userRestriction -> {
                    if (userRestriction.gameJoinRestriction().active()) {
                        return Mono.empty(); // User is banned - success case
                    }
                    return Mono.error(new UserIsNotRobloxBannedException(gameConfig.name()));
                });
    }

    private void logAppealSubmission(AppealSubmissionContextFactory.AppealSubmissionContext context) {
        log.info(
                "Processing appeal submission - Game: '{}', User: '{}' ({}), Punishment: '{}', Platform: '{}'",
                context.normalizedGameName(),
                context.username(),
                context.discordUserId(),
                context.punishmentType(),
                context.platform());
    }

    private Mono<Snowflake> processAppealSubmission(
            ModalSubmitInteractionEvent event, AppealSubmissionContextFactory.AppealSubmissionContext context) {
        return retrieveUserRobloxAccount(context)
                .flatMap(robloxProfile -> createAppealChannelAndCase(event, context, robloxProfile))
                .flatMap(appealData -> populateChannelWithDetails(event, appealData))
                .map(AppealCreationResult::channelId);
    }

    private Mono<Void> checkForExistingPendingCases(AppealSubmissionContextFactory.AppealSubmissionContext context) {
        return Mono.fromCallable(() -> caseRepository.findAppealCase(
                        context.normalizedGameName(),
                        context.punishmentType(),
                        context.discordUserId(),
                        AppealVerdict.PENDING,
                        context.platform()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(existingCase -> {
                    if (existingCase.isPresent()) {
                        logDuplicateAppeal(context);
                        return Mono.error(new ExistingPendingCaseException());
                    }
                    return Mono.empty();
                });
    }

    private void logDuplicateAppeal(AppealSubmissionContextFactory.AppealSubmissionContext context) {
        log.info(
                "Rejecting duplicate appeal - User: {}, Game: '{}', Punishment: '{}', Platform: '{}'",
                context.discordUserId(),
                context.normalizedGameName(),
                context.punishmentType(),
                context.platform());
    }

    private Mono<RobloxProfileDto> retrieveUserRobloxAccount(AppealSubmissionContextFactory.AppealSubmissionContext context) {
        final String roverToken =
                TokenHelper.retrieveRoverTokenForGame(context.normalizedGameName(), ServerType.APPEAL);

        return getRobloxAccountForUser(context.guildId(), context.discordUserId(), roverToken)
                .map(robloxAccount -> String.valueOf(robloxAccount.robloxId()))
                .flatMap(this::fetchRobloxProfile)
                .onErrorMap(HttpClientErrorException.NotFound.class,
                        ignored -> new RobloxAccountNotVerifiedException());
    }

    private Mono<RobloxProfileDto> fetchRobloxProfile(String robloxId) {
        return Mono.fromCallable(() -> openCloudClient.getRobloxProfile(robloxId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<AppealCreationResult> createAppealChannelAndCase(
            ModalSubmitInteractionEvent event, AppealSubmissionContextFactory.AppealSubmissionContext context, RobloxProfileDto robloxProfile) {

        return createAppealChannel(context, robloxProfile.name())
                .flatMap(channelId -> persistAppealCase(event, context, robloxProfile.id(), channelId)
                        .map(caseEntity -> new AppealCreationResult(channelId, robloxProfile, caseEntity)));
    }

    private Mono<AppealCreationResult> populateChannelWithDetails(
            ModalSubmitInteractionEvent event, AppealCreationResult appealData) {

        return sendCaseDetailsMessage(appealData.channelId(), appealData.caseEntity())
                .then(fetchAndSendRobloxProfileMessage(appealData.channelId(), appealData.robloxProfile()))
                .then(sendCaseHistoryMessage(
                        appealData.channelId(),
                        event.getInteraction().getUser().getId().asString(),
                        appealData.robloxProfile().id(),
                        appealData.robloxProfile().name()))
                .thenReturn(appealData);
    }

    private Mono<Snowflake> createAppealChannel(AppealSubmissionContextFactory.AppealSubmissionContext context, String robloxUsername) {
        final String channelName = generateChannelName(context, robloxUsername);

        return getAppealsCategoryId(context.guildId())
                .flatMap(categoryId -> createTextChannelWithPermissions(context, channelName, categoryId));
    }

    private String generateChannelName(AppealSubmissionContextFactory.AppealSubmissionContext context, String robloxUsername) {
        final String username = Optional.ofNullable(robloxUsername)
                .filter(name -> !name.isBlank())
                .orElse(context.username());

        return String.format(
                CHANNEL_NAME_FORMAT,
                context.platform().name().toLowerCase(),
                context.punishmentType().name().toLowerCase(),
                username);
    }

    private Mono<String> getAppealsCategoryId(String guildId) {
        return Mono.fromCallable(() -> guildConfigRepository.findByGuildIdAndConfigKey(
                        guildId, GuildConfig.OPEN_APPEALS_CATEGORY_ID))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(this::validateGuildConfig);
    }

    private Mono<String> validateGuildConfig(Optional<GuildConfigEntity> guildConfigOpt) {
        return guildConfigOpt
                .map(config -> Mono.just(config.getConfigValue()))
                .orElseGet(() -> Mono.error(new IncorrectServerSetupException()));
    }

    private Mono<Snowflake> createTextChannelWithPermissions(
            AppealSubmissionContextFactory.AppealSubmissionContext context, String channelName, String categoryId) {

        final TextChannelCreateSpec channelSpec = buildChannelCreateSpec(context, channelName, categoryId);

        return gatewayDiscordClient
                .getGuildById(Snowflake.of(context.guildId()))
                .flatMap(guild -> guild.createTextChannel(channelSpec))
                .map(Channel::getId);
    }

    private TextChannelCreateSpec buildChannelCreateSpec(
            AppealSubmissionContextFactory.AppealSubmissionContext context, String channelName, String categoryId) {

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

    private Mono<Void> sendCaseDetailsMessage(Snowflake channelId, CaseEntity caseEntity) {
        return getTextChannel(channelId)
                .flatMap(channel -> channel.createMessage(CaseInfoMessage.create(caseEntity)))
                .then();
    }

    private Mono<Void> fetchAndSendRobloxProfileMessage(Snowflake channelId, RobloxProfileDto robloxProfile) {
        return Mono.fromCallable(() -> openCloudClient.getRobloxAvatar(robloxProfile.id()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(avatar -> sendRobloxProfileMessage(channelId, robloxProfile, avatar));
    }

    private Mono<Void> sendRobloxProfileMessage(Snowflake channelId, RobloxProfileDto profile, RobloxAvatarDto avatar) {
        return getTextChannel(channelId)
                .flatMap(channel -> channel.createMessage(RobloxProfileMessage.create(profile, avatar)))
                .then();
    }

    private Mono<Void> sendCaseHistoryMessage(
            Snowflake channelId, String discordUserId, String robloxId, String username) {

        return Mono.fromCallable(() -> caseRepository.getCasesOfAppealer(discordUserId, robloxId))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(cases -> getTextChannel(channelId)
                        .flatMap(channel -> channel.createMessage(CaseHistoryMessage.create(cases, robloxId, username))))
                .then();
    }

    private Mono<TextChannel> getTextChannel(Snowflake channelId) {
        return gatewayDiscordClient.getChannelById(channelId).cast(TextChannel.class);
    }

    private Mono<CaseEntity> persistAppealCase(
            ModalSubmitInteractionEvent event, AppealSubmissionContextFactory.AppealSubmissionContext context, String robloxId, Snowflake channelId) {

        final CaseEntity caseEntity = buildCaseEntity(event, context, robloxId, channelId.asString());

        return Mono.fromCallable(() -> caseRepository.save(caseEntity)).subscribeOn(Schedulers.boundedElastic());
    }

    private CaseEntity buildCaseEntity(
            ModalSubmitInteractionEvent event, AppealSubmissionContextFactory.AppealSubmissionContext context, String robloxId, String channelId) {

        final AppealModalData modalData = extractModalData(event, context.platform());

        final CaseEntity caseEntity = new CaseEntity();
        caseEntity.setAppealerDiscordId(context.discordUserId());
        caseEntity.setAppealerRobloxId(robloxId);
        caseEntity.setChannelId(channelId);
        caseEntity.setPunishmentType(context.punishmentType());
        caseEntity.setPunishmentReason(modalData.punishmentReason());
        caseEntity.setGame(context.normalizedGameName());
        caseEntity.setAppealReason(modalData.appealReason());
        caseEntity.setVideoUrl(context.videoLink());
        caseEntity.setAppealVerdict(AppealVerdict.PENDING);
        caseEntity.setAppealPlatform(context.platform());
        caseEntity.setAppealedAt(context.submittedAt());

        return caseEntity;
    }

    private AppealModalData extractModalData(ModalSubmitInteractionEvent event, AppealPlatform platform) {
        return switch (platform) {
            case DISCORD -> new AppealModalData(
                    DiscordAppealModal.INSTANCE.getPunishmentReason(event),
                    DiscordAppealModal.INSTANCE.getAppealReason(event)
            );
            case GAME -> new AppealModalData(
                    GameAppealModal.INSTANCE.getPunishmentReason(event),
                    GameAppealModal.INSTANCE.getAppealReason(event)
            );
        };
    }

    private Mono<Void> sendSuccessResponse(ModalSubmitInteractionEvent event, String userId, Snowflake channelId) {
        final String message = String.format(SUCCESS_MESSAGE_FORMAT, userId, channelId.asString());
        return event.editReply(message).then();
    }

    private void logSuccessfulSubmission(AppealSubmissionContextFactory.AppealSubmissionContext context, Instant startTime) {
        final long duration = Instant.now().toEpochMilli() - startTime.toEpochMilli();
        log.info(
                "Appeal channel created successfully - User: {} ({}), Game: '{}', Platform: '{}', Duration: {}ms",
                context.username(),
                context.discordUserId(),
                context.normalizedGameName(),
                context.platform(),
                duration);
    }

    private Mono<Void> handleModalSubmissionError(ModalSubmitInteractionEvent event, Throwable error) {
        boolean isExpected = error instanceof AppealException;
        String message = isExpected ? error.getMessage() : DEFAULT_ERROR_MESSAGE;
        if (!isExpected) log.error("Unexpected error during appeal processing", error);

        return event.createFollowup(GenericErrorFollowUp.create(message, isExpected)).then();
    }

    private Mono<Void> handleTimeout(ModalSubmitInteractionEvent event, Throwable error) {
        log.error(
                "Appeal processing timed out for user: {}",
                event.getInteraction().getUser().getUsername(),
                error);
        return event.editReply(TIMEOUT_MESSAGE).then();
    }

    private void logUnexpectedError(Throwable error, Object trigger) {
        log.error("Unexpected error in modal listener", error);
    }

    // Helper records for better data encapsulation
    private record AppealCreationResult(
            Snowflake channelId,
            RobloxProfileDto robloxProfile,
            CaseEntity caseEntity) {}

    private record AppealModalData(
            String punishmentReason,
            String appealReason) {}
}