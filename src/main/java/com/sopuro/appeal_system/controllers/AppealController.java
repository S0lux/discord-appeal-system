package com.sopuro.appeal_system.controllers;

import com.sopuro.appeal_system.entities.CaseEntity;
import com.sopuro.appeal_system.exceptions.appeal.CaseNotFoundException;
import com.sopuro.appeal_system.services.AppealService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("appeals")
@RequiredArgsConstructor
public class AppealController {
    private final AppealService appealService;

    @GetMapping("/{access-code}")
    public ResponseEntity<CaseEntity> retrieveCaseByAccessCode(
            @PathVariable("access-code") String accessCode) {
        Optional<CaseEntity> result = appealService.retrieveCaseByAccessCode(accessCode);
        if (result.isEmpty()) throw new CaseNotFoundException();
        return ResponseEntity.ok(result.get());
    }
}
