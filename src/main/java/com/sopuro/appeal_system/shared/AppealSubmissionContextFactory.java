package com.sopuro.appeal_system.shared;

import com.sopuro.appeal_system.components.modals.DiscordAppealModal;
import com.sopuro.appeal_system.components.modals.GameAppealModal;
import com.sopuro.appeal_system.configs.AppealSystemConfig;
import com.sopuro.appeal_system.exceptions.appeal.MissingGuildContextException;
import com.sopuro.appeal_system.shared.enums.AppealPlatform;
import com.sopuro.appeal_system.shared.enums.PunishmentType;
import discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent;
import discord4j.core.object.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class AppealSubmissionContextFactory {

    private final AppealSystemConfig appealSystemConfig;

    public AppealSubmissionContext createContext(ModalSubmitInteractionEvent event, Instant submittedAt) {
        final User user = event.getInteraction().getUser();
        final String customId = event.getCustomId();
        final String guildId = extractGuildId(event);
        final AppealPlatform platform = determinePlatform(customId);

        return switch (platform) {
            case DISCORD -> createDiscordContext(event, user, guildId, platform, submittedAt);
            case GAME -> createGameContext(event, user, guildId, platform, submittedAt);
        };
    }

    private AppealSubmissionContext createDiscordContext(
            ModalSubmitInteractionEvent event,
            User user,
            String guildId,
            AppealPlatform platform,
            Instant submittedAt) {

        final String customId = event.getCustomId();
        final PunishmentType punishmentType = DiscordAppealModal.INSTANCE.getPunishmentTypeFromModalId(customId);

        return new AppealSubmissionContext(
                platform,
                guildId,
                user.getId().asString(),
                user.getUsername(),
                null, // No video link for Discord appeals
                appealSystemConfig.getGameConfigByServerId(guildId).normalizedName(),
                punishmentType,
                submittedAt);
    }

    private AppealSubmissionContext createGameContext(
            ModalSubmitInteractionEvent event,
            User user,
            String guildId,
            AppealPlatform platform,
            Instant submittedAt) {

        final String videoLink = GameAppealModal.INSTANCE.getVideoLink(event);

        return new AppealSubmissionContext(
                platform,
                guildId,
                user.getId().asString(),
                user.getUsername(),
                videoLink,
                appealSystemConfig.getGameConfigByServerId(guildId).normalizedName(),
                PunishmentType.BAN, // Game appeals are always for bans
                submittedAt);
    }

    private String extractGuildId(ModalSubmitInteractionEvent event) {
        return event.getInteraction()
                .getGuildId()
                .orElseThrow(MissingGuildContextException::new)
                .asString();
    }

    private AppealPlatform determinePlatform(String customId) {
        if (customId.startsWith(DiscordAppealModal.INSTANCE.getModalPrefix())) {
            return AppealPlatform.DISCORD;
        } else if (customId.startsWith(GameAppealModal.INSTANCE.getModalPrefix())) {
            return AppealPlatform.GAME;
        }
        throw new IllegalArgumentException("Unknown modal type: " + customId);
    }

    public record AppealSubmissionContext(
            AppealPlatform platform,
            String guildId,
            String discordUserId,
            String username,
            String videoLink,
            String normalizedGameName,
            PunishmentType punishmentType,
            Instant submittedAt) {}
}