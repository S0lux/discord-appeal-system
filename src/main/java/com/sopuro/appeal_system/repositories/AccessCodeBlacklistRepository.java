package com.sopuro.appeal_system.repositories;

import com.sopuro.appeal_system.entities.AccessCodeBlacklistEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccessCodeBlacklistRepository extends JpaRepository<AccessCodeBlacklistEntity, String> {
    Optional<AccessCodeBlacklistEntity> findByAccessCode(String accessCode);
}
