package com.sopuro.appeal_system.services;

import com.sopuro.appeal_system.entities.CaseEntity;
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
public class AppealService {
    private final CaseRepository caseRepository;

    public Optional<CaseEntity> retrieveCaseByAccessCode(String accessCode) {
        EncryptionHelper.CaseAccessDetails details = EncryptionHelper.decryptCaseAccessCode(accessCode);
        if (details == null) return Optional.empty();

        return caseRepository.findById(UUID.fromString(details.caseId()));
    }
}
