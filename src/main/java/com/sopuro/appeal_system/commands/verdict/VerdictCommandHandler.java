package com.sopuro.appeal_system.commands.verdict;

import com.sopuro.appeal_system.clients.opencloud.OpenCloudClient;
import com.sopuro.appeal_system.clients.opencloud.dtos.RobloxAvatarDto;
import com.sopuro.appeal_system.clients.opencloud.dtos.RobloxProfileDto;
import com.sopuro.appeal_system.clients.rover.RoverClient;
import com.sopuro.appeal_system.commands.SlashCommand;
import com.sopuro.appeal_system.components.messages.CaseLogDirectMessage;
import com.sopuro.appeal_system.components.messages.CaseLogMessage;
import com.sopuro.appeal_system.configs.AppealSystemConfig;
import com.sopuro.appeal_system.dtos.GameConfigDto;
import com.sopuro.appeal_system.entities.CaseEntity;
import com.sopuro.appeal_system.exceptions.AppealException;
import com.sopuro.appeal_system.exceptions.appeal.AppealAlreadyCompletedException;
import com.sopuro.appeal_system.exceptions.appeal.IncorrectServerSetupException;
import com.sopuro.appeal_system.exceptions.appeal.NotAppealChannelException;
import com.sopuro.appeal_system.exceptions.appeal.NotInCommunityServerException;
import com.sopuro.appeal_system.exceptions.rover.RoverUnbanFailedException;
import com.sopuro.appeal_system.repositories.CaseRepository;
import com.sopuro.appeal_system.repositories.GuildConfigRepository;
import com.sopuro.appeal_system.shared.enums.*;
import com.sopuro.appeal_system.shared.permissions.RoleOverwrites;
import com.sopuro.appeal_system.shared.utils.TokenHelper;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.PermissionOverwrite;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.spec.TextChannelEditSpec;
import discord4j.rest.http.client.ClientException;
import discord4j.rest.util.PermissionSet;
import io.netty.handler.codec.http.HttpResponseStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;

import java.time.Instant;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class VerdictCommandHandler implements SlashCommand {
    public static final String COMMAND_NAME = "verdict";

    private final AppealSystemConfig appealSystemConfig;
    private final GatewayDiscordClient gatewayDiscordClient;
    private final CaseRepository caseRepository;
    private final GuildConfigRepository guildConfigRepository;
    private final OpenCloudClient openCloudClient;
    private final RoverClient roverClient;

    @Value("${appeal-system.front-end.domain}")
    private String domainName;

    @Override
    public String getName() {
        return COMMAND_NAME;
    }

    @Override
    public List<AppealRole> allowedRoles() {
        return List.of(AppealRole.OVERSEER, AppealRole.JUDGE);
    }

    @Override
    public Mono<Void> preCondition(ChatInputInteractionEvent event) {
        Snowflake channelId = event.getInteraction().getChannelId();
        Mono<CaseEntity> caseEntityMono = getCaseEntityFromChannel(channelId);

        // Check if the channel is an appeal channel and if the appeal verdict is pending
        return caseEntityMono.flatMap(caseEntity -> {
            if (caseEntity.getAppealVerdict() != AppealVerdict.PENDING)
                return Mono.error(new AppealAlreadyCompletedException());
            return Mono.empty();
        });
    }

    @Override
    public Mono<Void> handle(ChatInputInteractionEvent event) {
        AppealVerdict verdict = getAppealVerdictFromCommand(event);
        String reason = getReasonFromCommand(event);
        Snowflake channelId = event.getInteraction().getChannelId();
        Snowflake discordId = event.getInteraction().getUser().getId();
        Snowflake guildId = event.getInteraction()
                .getGuildId()
                .orElseThrow(() -> new IllegalArgumentException("Guild ID is required for this command"));

        return getCaseEntityFromChannel(channelId)
                .flatMap(caseEntity ->
                        handleVerdict(caseEntity, verdict, reason, guildId.asString(), discordId.asString()))
                .flatMap(updatedCase -> {
                    log.info("Verdict '{}' applied to case {} with reason: {}", verdict, updatedCase.getId(), reason);
                    return getRobloxProfileAndAvatar(updatedCase.getAppealerRobloxId())
                            .flatMap(tuple -> {
                                RobloxProfileDto profile = tuple.getT1();
                                RobloxAvatarDto avatar = tuple.getT2();
                                GameConfigDto gameConfig =
                                        appealSystemConfig.getGameConfigByServerId(guildId.asString());

                                return Mono.when(
                                                gatewayDiscordClient
                                                        .getChannelById(channelId)
                                                        .cast(TextChannel.class)
                                                        .flatMap(channel -> channel.createMessage(
                                                                CaseLogMessage.create(updatedCase, profile, avatar))),
                                                gatewayDiscordClient
                                                        .getChannelById(Snowflake.of(gameConfig.logChannelId()))
                                                        .cast(TextChannel.class)
                                                        .flatMap(channel -> channel.createMessage(
                                                                CaseLogMessage.create(updatedCase, profile, avatar))))
                                        .then(event.editReply("Verdict applied successfully!"))
                                        .thenReturn(updatedCase);
                            });
                })
                .flatMap(caseEntity -> Mono.when(
                        moveCaseChannelToClosed(
                                Snowflake.of(caseEntity.getChannelId()),
                                Snowflake.of(caseEntity.getAppealerDiscordId())),
                        sendCaseLogDM(Snowflake.of(caseEntity.getAppealerDiscordId()), caseEntity)))
                .then();
    }

    private Mono<Tuple2<RobloxProfileDto, RobloxAvatarDto>> getRobloxProfileAndAvatar(String robloxId) {
        Mono<RobloxProfileDto> profileMono = Mono.fromCallable(() -> openCloudClient.getRobloxProfile(robloxId))
                .subscribeOn(Schedulers.boundedElastic());

        Mono<RobloxAvatarDto> avatarMono = Mono.fromCallable(() -> openCloudClient.getRobloxAvatar(robloxId))
                .subscribeOn(Schedulers.boundedElastic());

        return Mono.zip(profileMono, avatarMono).onErrorResume(throwable -> {
            log.error("Failed to fetch Roblox profile or avatar for ID {}: {}", robloxId, throwable.getMessage());
            return Mono.error(new AppealException("Failed to fetch Roblox profile or avatar"));
        });
    }

    private Mono<CaseEntity> getCaseEntityFromChannel(Snowflake channelId) {
        return Mono.fromCallable(() -> caseRepository
                        .findAppealCaseByChannelId(channelId.asString())
                        .orElse(null))
                .switchIfEmpty(Mono.error(new NotAppealChannelException()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private AppealVerdict getAppealVerdictFromCommand(ChatInputInteractionEvent event) {
        String decision = event.getOption("decision")
                .map(option -> option.getValue()
                        .orElseThrow(() -> new IllegalArgumentException("Decision option is required")))
                .map(ApplicationCommandInteractionOptionValue::asString)
                .orElseThrow(() -> new IllegalArgumentException("Decision option is required"));

        return switch (decision.toLowerCase()) {
            case "accept" -> AppealVerdict.ACCEPTED;
            case "reject" -> AppealVerdict.REJECTED;
            default -> throw new IllegalArgumentException("Invalid decision option: " + decision);
        };
    }

    private String getReasonFromCommand(ChatInputInteractionEvent event) {
        return event.getOption("reason")
                .map(option ->
                        option.getValue().orElseThrow(() -> new IllegalArgumentException("Reason option is required")))
                .map(ApplicationCommandInteractionOptionValue::asString)
                .orElse(null);
    }

    private Mono<CaseEntity> handleVerdict(
            CaseEntity caseEntity, AppealVerdict verdict, String reason, String appealServerId, String verdictBy) {
        GameConfigDto gameConfig = appealSystemConfig.getGameConfigByServerId(appealServerId);
        String robloxId = caseEntity.getAppealerRobloxId();

        if (verdict == AppealVerdict.ACCEPTED
                && caseEntity.getPunishmentType() == PunishmentType.BAN
                && caseEntity.getAppealPlatform() == AppealPlatform.DISCORD) {
            return unbanFromRover(gameConfig.communityServerId(), robloxId, gameConfig.normalizedName())
                    .then(unbanFromCommunity(
                            gameConfig.communityServerId(),
                            caseEntity.getAppealerDiscordId(),
                            gameConfig.normalizedName()))
                    .then(updateCaseWithVerdict(caseEntity, verdict, reason, verdictBy));
        }

        // Removing warnings is a manual process that can't be automated, so does nothing programmatically
        if (verdict == AppealVerdict.ACCEPTED
                && caseEntity.getPunishmentType() == PunishmentType.WARN
                && caseEntity.getAppealPlatform() == AppealPlatform.DISCORD) {
            return updateCaseWithVerdict(caseEntity, verdict, reason, verdictBy);
        }

        // Do nothing basically
        if (verdict == AppealVerdict.REJECTED) {
            return updateCaseWithVerdict(caseEntity, verdict, reason, verdictBy);
        }

        return Mono.error(new AppealException("Unknown verdict handling case"));
    }

    private Mono<Void> unbanFromRover(String guildId, String robloxId, String gameName) {
        return Mono.fromSupplier(() -> TokenHelper.retrieveRoverTokenForGame(gameName, ServerType.COMMUNITY))
                .flatMap(roverToken -> performRoverUnban(guildId, robloxId, roverToken, gameName))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<Void> performRoverUnban(String guildId, String robloxId, String roverToken, String gameName) {
        return Mono.fromCallable(() -> roverClient.deleteBan(guildId, robloxId, roverToken))
                .doOnSuccess(result ->
                        log.debug("Successfully unbanned robloxId '{}' from Rover for game {}", robloxId, gameName))
                .onErrorResume(throwable -> handleRoverUnbanError(throwable, guildId, robloxId, gameName))
                .then();
    }

    private Mono<Void> handleRoverUnbanError(Throwable throwable, String guildId, String robloxId, String gameName) {
        if (throwable instanceof HttpClientErrorException.NotFound) {
            log.info("Rover ban not found for guildId: {}, robloxId: {} - treating as success", guildId, robloxId);
            return Mono.empty();
        }

        log.error("Failed to unban robloxId '{}' from Rover for game: '{}'", robloxId, gameName, throwable);
        return Mono.error(new RoverUnbanFailedException(
                String.format("Failed to unban %s from Rover: %s", robloxId, throwable.getMessage())));
    }

    private Mono<Void> unbanFromCommunity(String communityId, String discordId, String reason) {
        return validateAndGetGuild(communityId)
                .flatMap(guild -> performDiscordUnban(guild, discordId, reason, communityId));
    }

    private Mono<Guild> validateAndGetGuild(String communityId) {
        return gatewayDiscordClient
                .getGuildById(Snowflake.of(communityId))
                .switchIfEmpty(Mono.error(new NotInCommunityServerException(
                        String.format("Guild not found or bot not in guild: %s", communityId))));
    }

    private Mono<Void> performDiscordUnban(Guild guild, String discordId, String reason, String communityId) {
        return guild.unban(Snowflake.of(discordId), reason)
                .onErrorResume(throwable -> handleDiscordUnbanError(throwable, discordId, communityId));
    }

    private Mono<Void> handleDiscordUnbanError(Throwable throwable, String discordId, String communityId) {
        if (throwable instanceof ClientException clientException) {
            return handleClientException(clientException, discordId, communityId);
        }

        log.error(
                "Unexpected error while unbanning Discord user '{}' from community '{}': {}",
                discordId,
                communityId,
                throwable.getMessage());
        return Mono.error(new AppealException(
                String.format("Unexpected error unbanning user %s: %s", discordId, throwable.getMessage())));
    }

    private Mono<CaseEntity> updateCaseWithVerdict(
            CaseEntity caseEntity, AppealVerdict verdict, String reason, String verdictBy) {
        caseEntity.setAppealVerdict(verdict);
        caseEntity.setVerdictReason(reason);
        caseEntity.setClosedAt(Instant.now());
        caseEntity.setVerdictBy(verdictBy);

        return Mono.fromCallable(() -> caseRepository.save(caseEntity)).subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<Void> moveCaseChannelToClosed(Snowflake channelSnowflake, Snowflake appealerId) {
        return gatewayDiscordClient
                .getChannelById(channelSnowflake)
                .cast(TextChannel.class)
                .flatMap(channel -> {
                    GameConfigDto gameConfig = appealSystemConfig.getGameConfigByServerId(
                            channel.getGuildId().asString());

                    return Mono.fromCallable(() -> guildConfigRepository.findByGuildIdAndConfigKey(
                                    gameConfig.appealServerId(), GuildConfig.CLOSED_APPEALS_CATEGORY_ID))
                            .subscribeOn(Schedulers.boundedElastic())
                            .flatMap(config -> {
                                if (config.isEmpty()) return Mono.error(new IncorrectServerSetupException());
                                else {
                                    String closedCategoryId = config.get().getConfigValue();
                                    return channel.edit(TextChannelEditSpec.create()
                                            // Move to closed category
                                            .withParentIdOrNull(Snowflake.of(closedCategoryId))
                                            // Update the permissions
                                            .withPermissionOverwrites(List.of(
                                                    PermissionOverwrite.forMember(
                                                            appealerId,
                                                            RoleOverwrites.Member.CLOSED_PERMISSIONS,
                                                            PermissionSet.all()),
                                                    PermissionOverwrite.forRole(
                                                            channel.getGuildId(),
                                                            PermissionSet.none(),
                                                            PermissionSet.all()),
                                                    PermissionOverwrite.forRole(
                                                            Snowflake.of(gameConfig.appealJudgeRoleId()),
                                                            RoleOverwrites.Judge.CLOSED_PERMISSIONS,
                                                            PermissionSet.all()),
                                                    PermissionOverwrite.forRole(
                                                            Snowflake.of(gameConfig.appealOverseerRoleId()),
                                                            RoleOverwrites.Overseer.CLOSED_PERMISSIONS,
                                                            PermissionSet.all()))));
                                }
                            });
                })
                .then();
    }

    private Mono<Void> sendCaseLogDM(Snowflake recipientId, CaseEntity caseEntity) {
        return gatewayDiscordClient
                .getUserById(recipientId)
                .flatMap(user -> user.getPrivateChannel()
                        .flatMap(privateChannel ->
                                privateChannel.createMessage(CaseLogDirectMessage.create(caseEntity, domainName))))
                .then();
    }

    private Mono<Void> handleClientException(ClientException clientException, String discordId, String communityId) {
        if (clientException.getStatus() == HttpResponseStatus.NOT_FOUND) {
            log.info("Discord user '{}' not banned in community '{}' - treating as success", discordId, communityId);
            return Mono.empty();
        }

        log.error(
                "Failed to unban Discord user '{}' from community '{}': {}",
                discordId,
                communityId,
                clientException.getMessage());
        return Mono.error(new AppealException(String.format(
                "Failed to unban user %s from community %s: %s",
                discordId, communityId, clientException.getMessage())));
    }
}
