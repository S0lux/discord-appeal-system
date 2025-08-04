package com.sopuro.appeal_system.configs;

import com.sopuro.appeal_system.dtos.GameConfigDto;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

@ConfigurationProperties(prefix = "appeal-system")
@RequiredArgsConstructor
@Getter
public class AppealSystemConfig {
    private final List<GameConfigDto> games;

    public List<String> getAppealServerIds() {
        return games.stream()
                .map(GameConfigDto::appealServerId)
                .toList();
    }

    public List<String> getCommunityServerIds() {
        return games.stream()
                .map(GameConfigDto::communityServerId)
                .toList();
    }

    public List<String> getAllRegisteredServerIds() {
        return games.stream()
                .flatMap(gameConfig -> Stream.of(gameConfig.appealServerId(), gameConfig.communityServerId()))
                .toList();
    }

    public GameConfigDto getGameConfigByServerId(String serverId) {
        return games.stream()
                .filter(game -> game.appealServerId().equals(serverId) || game.communityServerId().equals(serverId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No game configuration found for server ID: " + serverId));
    }

    public Boolean isAppealServer(String serverId) {
        return getAppealServerIds().contains(serverId);
    }

    public Boolean isCommunityServer(String serverId) {
        return getCommunityServerIds().contains(serverId);
    }

    public String getJudgeRoleId(String serverId) {
        return getGameConfigByServerId(serverId).appealJudgeRoleId();
    }

    public String getOverseerRoleId(String serverId) {
        return getGameConfigByServerId(serverId).appealOverseerRoleId();
    }

    @Bean("appealProcessingExecutor")
    public Executor appealProcessingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("appeal-processing-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Bean
    public Scheduler appealProcessingScheduler(Executor appealProcessingExecutor) {
        return Schedulers.fromExecutor(appealProcessingExecutor);
    }
}
