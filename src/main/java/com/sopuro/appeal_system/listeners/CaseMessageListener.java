package com.sopuro.appeal_system.listeners;

import com.sopuro.appeal_system.configs.AppealSystemConfig;
import com.sopuro.appeal_system.entities.CaseEntity;
import com.sopuro.appeal_system.entities.MessageLogEntity;
import com.sopuro.appeal_system.exceptions.appeal.MessageNotFromAppealServerException;
import com.sopuro.appeal_system.repositories.CaseRepository;
import com.sopuro.appeal_system.repositories.MessageLogRepository;
import com.sopuro.appeal_system.shared.enums.AppealVerdict;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.event.domain.message.MessageDeleteEvent;
import discord4j.core.event.domain.message.MessageEvent;
import discord4j.core.event.domain.message.MessageUpdateEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class CaseMessageListener {
    private final CaseRepository caseRepository;
    private final MessageLogRepository messageLogRepository;
    private final AppealSystemConfig appealSystemConfig;

    public CaseMessageListener(
            CaseRepository caseRepository,
            GatewayDiscordClient gatewayDiscordClient,
            MessageLogRepository messageLogRepository,
            AppealSystemConfig appealSystemConfig) {
        this.caseRepository = caseRepository;
        this.messageLogRepository = messageLogRepository;
        this.appealSystemConfig = appealSystemConfig;

        gatewayDiscordClient
                .on(MessageEvent.class)
                .flatMap(event -> {
                    if (event instanceof MessageCreateEvent createEvent) {
                        return handlePersistMessage(createEvent);
                    } else if (event instanceof MessageUpdateEvent updateEvent) {
                        return handleUpdateMessage(updateEvent);
                    } else if (event instanceof MessageDeleteEvent deleteEvent) {
                        return handleDeleteMessage(deleteEvent);
                    }
                    return Mono.empty();
                })
                .onErrorContinue((error, obj) -> {
                    if (error instanceof MessageNotFromAppealServerException) {
                        // Silently ignore messages not from appeal servers
                        log.debug("Message not from appeal server: {}", error.getMessage());
                    } else {
                        log.error("Error processing message event: ", error);
                    }
                })
                .subscribe();
    }

    private Mono<Void> handlePersistMessage(MessageCreateEvent event) {
        return ensureMessageFromAppealServer(event)
                .then(getCaseIdFromMessage(event))
                .flatMap(caseId -> persistMessageForCase(event, caseId))
                .then();
    }

    private Mono<Void> handleUpdateMessage(MessageUpdateEvent event) {
        return ensureMessageFromAppealServer(event)
                .then(getCaseIdFromMessage(event))
                .flatMap(caseId -> updateMessageForCase(event, caseId))
                .then();
    }

    private Mono<Void> handleDeleteMessage(MessageDeleteEvent event) {
        return ensureMessageFromAppealServer(event)
                .then(getCaseIdFromMessage(event))
                .flatMap(ignored -> deleteLoggedMessage(event))
                .then();
    }

    private Mono<Void> ensureMessageFromAppealServer(MessageCreateEvent event) {
        return event.getGuildId()
                .map(guildId -> {
                    if (appealSystemConfig.getAppealServerIds().contains(guildId.asString())) {
                        return Mono.<Void>empty();
                    }
                    return Mono.<Void>error(new MessageNotFromAppealServerException(
                            event.getMessage().getId().asString(), guildId.asString()));
                })
                .orElse(Mono.error(new MessageNotFromAppealServerException(
                        event.getMessage().getId().asString(), "DM"))); // Handle DM messages
    }

    private Mono<Void> ensureMessageFromAppealServer(MessageUpdateEvent event) {
        return event.getGuildId()
                .map(guildId -> {
                    if (appealSystemConfig.getAppealServerIds().contains(guildId.asString())) {
                        return Mono.<Void>empty();
                    }
                    return Mono.<Void>error(new MessageNotFromAppealServerException(
                            event.getMessageId().asString(), guildId.asString()));
                })
                .orElse(Mono.error(new MessageNotFromAppealServerException(
                        event.getMessageId().asString(), "DM"))); // Handle DM messages
    }

    private Mono<Void> ensureMessageFromAppealServer(MessageDeleteEvent event) {
        return event.getGuildId()
                .map(guildId -> {
                    if (appealSystemConfig.getAppealServerIds().contains(guildId.asString())) {
                        return Mono.<Void>empty();
                    }
                    return Mono.<Void>error(new MessageNotFromAppealServerException(
                            event.getMessageId().asString(), guildId.asString()));
                })
                .orElse(Mono.error(new MessageNotFromAppealServerException(
                        event.getMessageId().asString(), "DM"))); // Handle DM messages
    }

    private Mono<UUID> getCaseIdFromMessage(MessageCreateEvent event) {
        Snowflake channelId = event.getMessage().getChannelId();
        return Mono.fromCallable(() -> caseRepository.findAppealCase(channelId.asString(), AppealVerdict.PENDING))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(optionalCase ->
                        optionalCase.map(entity -> Mono.just(entity.getId())).orElse(Mono.empty()));
    }

    private Mono<UUID> getCaseIdFromMessage(MessageUpdateEvent event) {
        Mono<Snowflake> channelIdMono = event.getMessage().flatMap(message -> Mono.just(message.getChannelId()));
        return channelIdMono
                .flatMap(channelId -> Mono.fromCallable(
                                () -> caseRepository.findAppealCase(channelId.asString(), AppealVerdict.PENDING))
                        .subscribeOn(Schedulers.boundedElastic()))
                .flatMap(appealCase ->
                        appealCase.map(entity -> Mono.just(entity.getId())).orElse(Mono.empty()));
    }

    private Mono<UUID> getCaseIdFromMessage(MessageDeleteEvent event) {
        Snowflake channelId = event.getChannelId();
        return Mono.fromCallable(() -> caseRepository.findAppealCase(channelId.asString(), AppealVerdict.PENDING))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(optionalCase ->
                        optionalCase.map(entity -> Mono.just(entity.getId())).orElse(Mono.empty()));
    }

    private Mono<Void> persistMessageForCase(MessageCreateEvent event, UUID caseId) {
        String messageId = event.getMessage().getId().asString();
        String messageContent = event.getMessage().getContent();
        Instant messageCreationTime = event.getMessage().getTimestamp();
        Instant messageEditedTime = event.getMessage().getEditedTimestamp().orElse(null);
        String authorId = event.getMessage()
                .getAuthor()
                .map(author -> author.getId().asString())
                .orElse("unknown");

        return Mono.fromCallable(() -> {
                    CaseEntity appealCase = caseRepository.getReferenceById(caseId);

                    // TODO: Add support for attachments(?) and components

                    MessageLogEntity messageLog = MessageLogEntity.builder()
                            .id(messageId)
                            .appealCase(appealCase)
                            .authorId(authorId)
                            .content(messageContent)
                            .creationTimestamp(messageCreationTime)
                            .lastEditedTimestamp(messageEditedTime)
                            .build();

                    messageLogRepository.save(messageLog);
                    return null;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private Mono<Void> updateMessageForCase(MessageUpdateEvent event, UUID caseId) {
        return event
                .getMessage()
                .flatMap(message -> {
                    String messageId = message.getId().asString();
                    String messageContent = message.getContent();
                    Instant messageEditedTime = message.getEditedTimestamp().orElse(null);

                    return Mono.fromCallable(() -> {
                        Optional<MessageLogEntity> appealCase = messageLogRepository.findByIdAndCaseId(messageId, caseId);
                        if (appealCase.isPresent()) {
                            MessageLogEntity messageLog = appealCase.get();
                            messageLog.setContent(messageContent);
                            messageLog.setLastEditedTimestamp(messageEditedTime);
                            messageLogRepository.save(messageLog);
                        } else {
                            log.warn("Message log not found for ID to be updated: {}", messageId);
                        }

                        return null;
                    }).subscribeOn(Schedulers.boundedElastic());
                })
                .then();
    }

    private Mono<Void> deleteLoggedMessage(MessageDeleteEvent event) {
        return Mono.fromCallable(() -> {
                    String messageId = event.getMessageId().asString();
                    Optional<MessageLogEntity> messageLog = messageLogRepository.findById(messageId);
                    if (messageLog.isPresent()) {
                        messageLogRepository.delete(messageLog.get());
                    } else {
                        log.warn("Message log not found for ID to be deleted: {}", messageId);
                    }
                    return null;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }
}