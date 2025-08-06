package com.sopuro.appeal_system.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.*;

import java.time.Instant;

@Entity
@Builder
@Getter
@Setter
@ToString
@RequiredArgsConstructor
@AllArgsConstructor
public class AccessCodeBlacklistEntity {
    @Id
    private String accessCode;

    @Column
    private String createdBy;

    @Column
    @Builder.Default
    private Instant createdAt = Instant.now();
}
