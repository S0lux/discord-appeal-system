package com.sopuro.appeal_system.repositories;

import com.sopuro.appeal_system.entities.CaseEntity;
import com.sopuro.appeal_system.shared.enums.AppealPlatform;
import com.sopuro.appeal_system.shared.enums.AppealVerdict;
import com.sopuro.appeal_system.shared.enums.PunishmentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CaseRepository extends JpaRepository<CaseEntity, UUID> {
    @Query("SELECT c FROM CaseEntity c WHERE c.game = :game AND c.punishmentType = :punishmentType "
            + "AND c.appealerDiscordId = :appealerDiscordId AND c.appealVerdict = :appealVerdict "
            + "AND c.appealPlatform = :appealPlatform")
    Optional<CaseEntity> findAppealCase(
            String game,
            PunishmentType punishmentType,
            String appealerDiscordId,
            AppealVerdict appealVerdict,
            AppealPlatform appealPlatform);

    @Query("SELECT c FROM CaseEntity c WHERE c.channelId = :channelId AND c.appealVerdict = :appealVerdict")
    Optional<CaseEntity> findAppealCase(String channelId, AppealVerdict appealVerdict);

    @Query("SELECT c FROM CaseEntity c WHERE c.channelId = :channelId")
    Optional<CaseEntity> findAppealCaseByChannelId(String channelId);

    @Query("SELECT c FROM CaseEntity c WHERE c.appealerDiscordId = :appealerDiscordId "
            + "OR c.appealerRobloxId = :appealerRobloxId "
            + "ORDER BY c.appealedAt DESC")
    List<CaseEntity> getCasesOfAppealer(String appealerDiscordId, String appealerRobloxId);
}
