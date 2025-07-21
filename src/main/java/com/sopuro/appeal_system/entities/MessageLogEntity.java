package com.sopuro.appeal_system.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "message_logs")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MessageLogEntity {
    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    private CaseEntity appealCase;

    @Column(name = "author_id", nullable = false)
    private String authorId;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "creation_timestamp", nullable = false, updatable = false)
    private Instant creationTimestamp;

    @Column(name = "last_edited_timestamp")
    private Instant lastEditedTimestamp;
}
