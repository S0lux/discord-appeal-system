package com.sopuro.appeal_system.schedulers;

import com.sopuro.appeal_system.entities.CaseEntity;
import com.sopuro.appeal_system.repositories.CaseRepository;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.channel.Channel;
import discord4j.rest.http.client.ClientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class AppealCleanUpScheduler {
    private final CaseRepository caseRepository;
    private final GatewayDiscordClient gateway;

    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.MINUTES)
    public void cleanupOldCaseChannels() {
        try {
            List<CaseEntity> oldCases = caseRepository.getNotCleanedUpOldCases(1);

            if (oldCases.isEmpty()) {
                log.debug("No old cases found for cleanup");
                return;
            }

            log.info("Starting cleanup of {} old case channels", oldCases.size());

            Flux.fromIterable(oldCases)
                    .delayElements(Duration.ofSeconds(2))
                    .flatMap(caseEntity ->
                            gateway.getChannelById(Snowflake.of(caseEntity.getChannelId()))
                                    .flatMap(Channel::delete)
                                    .then(updateCaseAsCleanedUp(caseEntity))
                                    .doOnSuccess(saved -> log.debug("Successfully cleaned up case {}", saved.getId()))
                                    .onErrorResume(throwable -> handleCleanupError(caseEntity, throwable))
                    )
                    .doOnComplete(() -> log.info("Cleanup task completed"))
                    .timeout(Duration.ofMinutes(10)) // Prevent hanging indefinitely
                    .blockLast();

        } catch (Exception e) {
            log.error("Error during scheduled cleanup task", e);
        }
    }

    private Mono<CaseEntity> updateCaseAsCleanedUp(CaseEntity caseEntity) {
        return Mono.fromCallable(() -> {
                    caseEntity.setCleanedUpAt(Instant.now());
                    return caseRepository.save(caseEntity);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(e -> log.error("Failed to update case {} as cleaned up", caseEntity.getId(), e));
    }

    private Mono<CaseEntity> handleCleanupError(CaseEntity caseEntity, Throwable throwable) {
        if (throwable instanceof ClientException) {
            ClientException ce = (ClientException) throwable;

            // Channel already deleted - mark as cleaned up
            if (ce.getStatus().code() == 404) {
                log.debug("Channel {} for case {} already deleted",
                        caseEntity.getChannelId(), caseEntity.getId());
                return updateCaseAsCleanedUp(caseEntity);
            }

            // No permission - mark as cleaned up to avoid retry loops
            if (ce.getStatus().code() == 403) {
                log.warn("No permission to delete channel {} for case {}",
                        caseEntity.getChannelId(), caseEntity.getId());
                return updateCaseAsCleanedUp(caseEntity);
            }

            // Rate limited - log but don't mark as cleaned up for retry
            if (ce.getStatus().code() == 429) {
                log.warn("Rate limited while deleting channel {} for case {}",
                        caseEntity.getChannelId(), caseEntity.getId());
                return Mono.empty();
            }
        }

        log.error("Failed to cleanup case {} with channel {}",
                caseEntity.getId(), caseEntity.getChannelId(), throwable);
        return Mono.empty(); // Don't mark as cleaned up for genuine failures
    }
}
