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
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class CaseMessageListener {
    private final CaseRepository caseRepository;
    private final MessageLogRepository messageLogRepository;
    private final AppealSystemConfig appealSystemConfig;
    private final GatewayDiscordClient gatewayDiscordClient;

    @PostConstruct
    public void initializeEventHandlers() {
        gatewayDiscordClient
                .on(MessageEvent.class)
                .filterWhen(event -> isFromBot(event).map(result -> !result))
                .filterWhen(event -> isEphemeral(event).map(result -> !result))
                .flatMap(this::routeMessageEvent)
                .onErrorContinue(this::handleEventError)
                .subscribe();
    }

    private Mono<Boolean> isFromBot(MessageEvent event) {
        return switch (event) {
            case MessageCreateEvent createEvent ->
                Mono.just(createEvent.getMessage().getAuthor().map(User::isBot).orElse(false));
            case MessageUpdateEvent updateEvent ->
                updateEvent
                        .getMessage()
                        .map(message -> message.getAuthor().map(User::isBot).orElse(false));
            case MessageDeleteEvent deleteEvent ->
                deleteEvent
                        .getMessage()
                        .map(message ->
                                Mono.just(message.getAuthor().map(User::isBot).orElse(false)))
                        .orElse(Mono.just(false));
            default -> Mono.just(false);
        };
    }

    private Mono<Boolean> isEphemeral(MessageEvent event) {
        return switch (event) {
            case MessageCreateEvent createEvent ->
                Mono.just(createEvent.getMessage().getFlags().contains(Message.Flag.EPHEMERAL));
            case MessageUpdateEvent updateEvent ->
                updateEvent.getMessage().flatMap(message -> Mono.just(
                                message.getFlags().contains(Message.Flag.EPHEMERAL))
                        .defaultIfEmpty(false));
            case MessageDeleteEvent deleteEvent ->
                deleteEvent
                        .getMessage()
                        .map(message -> Mono.just(message.getFlags().contains(Message.Flag.EPHEMERAL)))
                        .orElse(Mono.just(false));
            default -> Mono.just(false);
        };
    }

    private Mono<Void> routeMessageEvent(MessageEvent event) {
        return switch (event) {
            case MessageCreateEvent createEvent -> handleMessageCreate(createEvent);
            case MessageUpdateEvent updateEvent -> handleMessageUpdate(updateEvent);
            case MessageDeleteEvent deleteEvent -> handleMessageDelete(deleteEvent);
            default -> Mono.empty();
        };
    }

    private void handleEventError(Throwable error, Object obj) {
        if (error instanceof MessageNotFromAppealServerException) {
            log.debug("Message not from appeal server: {}", error.getMessage());
        } else {
            log.error("Error processing message event for object {}: ", obj, error);
        }
    }

    private Mono<Void> handleMessageCreate(MessageCreateEvent event) {
        return validateMessageFromAppealServer(event)
                .then(findCaseByChannelId(event.getMessage().getChannelId()))
                .flatMap(caseId -> persistMessage(event, caseId))
                .onErrorResume(error -> {
                    if (!(error instanceof MessageNotFromAppealServerException))
                        log.warn(
                            "Failed to create log for message {}: {}",
                            event.getMessage().getId().asString(),
                            error.getMessage());

                    return Mono.empty();
                });
    }

    private Mono<Void> handleMessageUpdate(MessageUpdateEvent event) {
        return validateMessageFromAppealServer(event)
                .then(getChannelIdFromUpdateEvent(event))
                .flatMap(this::findCaseByChannelId)
                .flatMap(caseId -> updateMessage(event, caseId))
                .onErrorResume(error -> {
                    log.warn(
                            "Failed to handle message update for message {}: {}",
                            event.getMessageId().asString(),
                            error.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<Void> handleMessageDelete(MessageDeleteEvent event) {
        return validateMessageFromAppealServer(event).then(deleteMessage(event)).onErrorResume(error -> {
            log.warn(
                    "Failed to handle message delete for message {}: {}",
                    event.getMessageId().asString(),
                    error.getMessage());
            return Mono.empty();
        });
    }

    private Mono<Void> validateMessageFromAppealServer(MessageEvent event) {
        String messageId = getMessageId(event);
        Optional<Snowflake> guildId = getGuildId(event);

        if (guildId.isEmpty()) {
            return Mono.error(new MessageNotFromAppealServerException(messageId, "DM"));
        }

        String guildIdString = guildId.get().asString();
        if (!appealSystemConfig.getAppealServerIds().contains(guildIdString)) {
            return Mono.error(new MessageNotFromAppealServerException(messageId, guildIdString));
        }

        return Mono.empty();
    }

    private String getMessageId(MessageEvent event) {
        return switch (event) {
            case MessageCreateEvent createEvent ->
                createEvent.getMessage().getId().asString();
            case MessageUpdateEvent updateEvent -> updateEvent.getMessageId().asString();
            case MessageDeleteEvent deleteEvent -> deleteEvent.getMessageId().asString();
            default -> "unknown";
        };
    }

    private Optional<Snowflake> getGuildId(MessageEvent event) {
        return switch (event) {
            case MessageCreateEvent createEvent -> createEvent.getGuildId();
            case MessageUpdateEvent updateEvent -> updateEvent.getGuildId();
            case MessageDeleteEvent deleteEvent -> deleteEvent.getGuildId();
            default -> Optional.empty();
        };
    }

    private Mono<Snowflake> getChannelIdFromUpdateEvent(MessageUpdateEvent event) {
        return event.getMessage()
                .map(Message::getChannelId)
                .switchIfEmpty(Mono.error(new RuntimeException("Unable to get channel ID from update event")));
    }

    private Mono<UUID> findCaseByChannelId(Snowflake channelId) {
        return Mono.fromCallable(() -> caseRepository.findAppealCase(channelId.asString(), AppealVerdict.PENDING))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(optionalCase -> optionalCase
                        .map(Mono::just)
                        .map(caseMono -> caseMono.map(CaseEntity::getId))
                        .orElse(Mono.empty()));
    }

    private Mono<Void> persistMessage(MessageCreateEvent event, UUID caseId) {
        return Mono.fromCallable(() -> {
                    MessageLogEntity messageLog = buildMessageLogEntity(event, caseId);
                    messageLogRepository.save(messageLog);
                    log.debug("Persisted message {} for case {}", messageLog.getId(), caseId);
                    return null;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private MessageLogEntity buildMessageLogEntity(MessageCreateEvent event, UUID caseId) {
        var message = event.getMessage();
        CaseEntity appealCase = caseRepository.getReferenceById(caseId);

        return MessageLogEntity.builder()
                .id(message.getId().asString())
                .appealCase(appealCase)
                .authorId(message.getAuthor()
                        .map(author -> author.getId().asString())
                        .orElse("unknown"))
                .content(message.getContent())
                .creationTimestamp(message.getTimestamp())
                .lastEditedTimestamp(message.getEditedTimestamp().orElse(null))
                .build();
    }

    private Mono<Void> updateMessage(MessageUpdateEvent event, UUID caseId) {
        return event.getMessage()
                .flatMap(message -> Mono.fromCallable(() -> {
                            String messageId = message.getId().asString();
                            String messageContent = message.getContent();
                            Instant messageEditedTime =
                                    message.getEditedTimestamp().orElse(null);

                            Optional<MessageLogEntity> messageLogOpt =
                                    messageLogRepository.findByIdAndCaseId(messageId, caseId);

                            if (messageLogOpt.isPresent()) {
                                MessageLogEntity messageLog = messageLogOpt.get();
                                messageLog.setContent(messageContent);
                                messageLog.setLastEditedTimestamp(messageEditedTime);
                                messageLogRepository.save(messageLog);
                                log.debug("Updated message {} for case {}", messageId, caseId);
                            } else {
                                log.warn("Message log not found for update - ID: {}, Case: {}", messageId, caseId);
                            }
                            return null;
                        })
                        .subscribeOn(Schedulers.boundedElastic()))
                .then();
    }

    private Mono<Void> deleteMessage(MessageDeleteEvent event) {
        return Mono.fromCallable(() -> {
                    String messageId = event.getMessageId().asString();
                    Optional<MessageLogEntity> messageLogOpt = messageLogRepository.findById(messageId);

                    if (messageLogOpt.isPresent()) {
                        messageLogRepository.delete(messageLogOpt.get());
                        log.debug("Deleted message log for ID: {}", messageId);
                    }
                    return null;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }
}