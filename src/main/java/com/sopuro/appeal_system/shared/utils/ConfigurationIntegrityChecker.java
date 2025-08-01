package com.sopuro.appeal_system.shared.utils;

import com.sopuro.appeal_system.configs.AppealSystemConfig;
import com.sopuro.appeal_system.dtos.GameConfigDto;
import com.sopuro.appeal_system.shared.enums.ServerType;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ConfigurationIntegrityChecker {
    private final AppealSystemConfig appealSystemConfig;

    @PostConstruct
    public void checkConfigurationIntegrity() {
        log.info("Starting configuration integrity check...");
        roverTokenCheck();
        aesKeyCheck();
        log.info("Configuration integrity check completed successfully");
    }

    private void roverTokenCheck() {
        boolean hasErrors = false;

        for (GameConfigDto game : appealSystemConfig.getGames()) {
            String appealToken = TokenHelper.retrieveRoverTokenForGame(game.normalizedName(), ServerType.APPEAL);
            String communityToken = TokenHelper.retrieveRoverTokenForGame(game.normalizedName(), ServerType.COMMUNITY);

            if (appealToken == null || appealToken.isBlank()) {
                log.error("Missing or empty APPEAL rover token for game: {}", game.normalizedName());
                hasErrors = true;
            }
            if (communityToken == null || communityToken.isBlank()) {
                log.error("Missing or empty COMMUNITY rover token for game: {}", game.normalizedName());
                hasErrors = true;
            }
        }

        if (hasErrors) {
            throw new RuntimeException(
                    "Configuration integrity check failed: Missing or empty rover tokens detected. Check preceding logs for details.");
        }
    }

    private void aesKeyCheck() {
        String secretKey = System.getenv("AES_SECRET_KEY");
        String salt = System.getenv("AES_SALT");
        if (secretKey == null || secretKey.isBlank() || salt == null || salt.isBlank()) {
            throw new RuntimeException("Missing AES configuration details (secretKey and salt)");
        }
    }

    private void openCloudCheck() {
        if (System.getenv("OPEN_CLOUD_KEY") == null)
            throw new RuntimeException("Missing open cloud key for handling in-game bans");
    }
}