package com.sopuro.appeal_system.repositories;

import com.sopuro.appeal_system.entities.CaseEntity;
import com.sopuro.appeal_system.shared.enums.AppealPlatform;
import com.sopuro.appeal_system.shared.enums.AppealVerdict;
import com.sopuro.appeal_system.shared.enums.PunishmentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CaseRepository extends JpaRepository<CaseEntity, UUID> {
    @Query("SELECT c FROM CaseEntity c WHERE c.game = :game AND c.punishmentType = :punishmentType " +
            "AND c.appealerDiscordId = :appealerDiscordId AND c.appealVerdict = :appealVerdict " +
            "AND c.appealPlatform = :appealPlatform")
    Optional<CaseEntity> findAppealCase(@Param("game") String game,
                                        @Param("punishmentType") PunishmentType punishmentType,
                                        @Param("appealerDiscordId") String appealerDiscordId,
                                        @Param("appealVerdict") AppealVerdict appealVerdict,
                                        @Param("appealPlatform") AppealPlatform appealPlatform);
}
