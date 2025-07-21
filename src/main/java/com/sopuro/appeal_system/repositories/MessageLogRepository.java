package com.sopuro.appeal_system.repositories;

import com.sopuro.appeal_system.entities.MessageLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface MessageLogRepository extends JpaRepository<MessageLogEntity, String> {

    @Query("SELECT m FROM MessageLogEntity m WHERE m.appealCase.id = :caseId AND m.id = :messageId")
    Optional<MessageLogEntity> findByIdAndCaseId(String messageId, UUID caseId);

}
