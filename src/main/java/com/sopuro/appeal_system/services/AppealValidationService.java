package com.sopuro.appeal_system.services;

import com.sopuro.appeal_system.components.modals.DiscordAppealModal;
import com.sopuro.appeal_system.components.modals.GameAppealModal;
import com.sopuro.appeal_system.exceptions.appeal.InvalidAppealDataException;
import discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.net.MalformedURLException;
import java.net.URL;

@Service
@Slf4j
@RequiredArgsConstructor
public class AppealValidationService {

    private static final int MAX_REASON_LENGTH = 2000;
    private static final int MIN_REASON_LENGTH = 10;
    private static final String INVALID_APPEAL_REASON_MESSAGE =
            "Appeal reason must be between %d and %d characters";
    private static final String INVALID_PUNISHMENT_REASON_MESSAGE =
            "Punishment reason must be between %d and %d characters";
    private static final String INVALID_VIDEO_URL_MESSAGE =
            "Video URL must be a valid URL";

    public Mono<Void> validateDiscordAppeal(ModalSubmitInteractionEvent event) {
        return Mono.fromRunnable(() -> {
            final String appealReason = DiscordAppealModal.INSTANCE.getAppealReason(event);
            final String punishmentReason = DiscordAppealModal.INSTANCE.getPunishmentReason(event);

            validateReasonLength(appealReason, "Appeal", INVALID_APPEAL_REASON_MESSAGE);
            validateReasonLength(punishmentReason, "Punishment", INVALID_PUNISHMENT_REASON_MESSAGE);
        });
    }

    public Mono<Void> validateGameAppeal(ModalSubmitInteractionEvent event) {
        return Mono.fromRunnable(() -> {
            final String appealReason = GameAppealModal.INSTANCE.getAppealReason(event);
            final String punishmentReason = GameAppealModal.INSTANCE.getPunishmentReason(event);
            final String videoLink = GameAppealModal.INSTANCE.getVideoLink(event);

            validateReasonLength(appealReason, "Appeal", INVALID_APPEAL_REASON_MESSAGE);
            validateReasonLength(punishmentReason, "Punishment", INVALID_PUNISHMENT_REASON_MESSAGE);

            if (videoLink != null && !videoLink.isBlank()) {
                validateVideoUrl(videoLink);
            }
        });
    }

    private void validateReasonLength(String reason, String fieldName, String errorMessage) {
        if (reason == null || reason.isBlank()) {
            throw new InvalidAppealDataException(fieldName + " reason cannot be empty");
        }

        final String trimmedReason = reason.trim();
        if (trimmedReason.length() < MIN_REASON_LENGTH || trimmedReason.length() > MAX_REASON_LENGTH) {
            throw new InvalidAppealDataException(
                    String.format(errorMessage, MIN_REASON_LENGTH, MAX_REASON_LENGTH));
        }
    }

    private void validateVideoUrl(String videoUrl) {
        try {
            new URL(videoUrl);

            // Additional validation for common video platforms
            if (!isValidVideoUrl(videoUrl)) {
                throw new InvalidAppealDataException(
                        "Video URL must be from a supported platform (YouTube, Streamable, etc.)");
            }
        } catch (MalformedURLException e) {
            throw new InvalidAppealDataException(INVALID_VIDEO_URL_MESSAGE);
        }
    }

    private boolean isValidVideoUrl(String url) {
        final String lowerUrl = url.toLowerCase();
        return lowerUrl.contains("youtube.com") ||
                lowerUrl.contains("youtu.be") ||
                lowerUrl.contains("streamable.com") ||
                lowerUrl.contains("vimeo.com") ||
                lowerUrl.contains("twitch.tv") ||
                lowerUrl.contains("clips.twitch.tv");
    }
}