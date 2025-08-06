package com.sopuro.appeal_system.services;

import com.sopuro.appeal_system.entities.AccessCodeBlacklistEntity;
import com.sopuro.appeal_system.entities.CaseEntity;
import com.sopuro.appeal_system.repositories.AccessCodeBlacklistRepository;
import com.sopuro.appeal_system.repositories.CaseRepository;
import com.sopuro.appeal_system.shared.utils.EncryptionHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AppealHistoryService {
    private final CaseRepository caseRepository;
    private final AccessCodeBlacklistRepository blacklistRepository;

    public Optional<CaseEntity> retrieveCaseByAccessCode(String accessCode) {
        // Check if code is blacklisted
        Optional<AccessCodeBlacklistEntity> blacklistEntity = blacklistRepository.findByAccessCode(accessCode);
        if (blacklistEntity.isPresent()) return Optional.empty();

        // Start access code decoding process
        EncryptionHelper.CaseAccessDetails details = EncryptionHelper.decryptCaseAccessCode(accessCode);
        if (details == null) return Optional.empty();

        try {
            UUID caseId = UUID.fromString(details.caseId());
            return caseRepository.findById(caseId);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }
}
