package com.sopuro.appeal_system.entities;

import com.sopuro.appeal_system.shared.enums.AppealPlatform;
import com.sopuro.appeal_system.shared.enums.AppealVerdict;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "cases")
@EntityListeners(AuditingEntityListener.class)
@RequiredArgsConstructor
@Getter
@Setter
@ToString
public class CaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "appealer_discord_id", nullable = false)
    private String appealerDiscordId;

    @Column(name = "appealer_roblox_id", nullable = false)
    private String appealerRobloxId;

    @Column(name = "appeal_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private AppealPlatform appealType;

    @Column(name = "appeal_verdict", nullable = false)
    @Enumerated(EnumType.STRING)
    private AppealVerdict appealVerdict;

    @Column(name = "appeal_reason", nullable = false)
    private String appealReason;

    @Column(name = "punishment_type", nullable = false)
    private String punishmentType;

    @Column(name = "punishment_reason", nullable = false)
    private String punishmentReason;

    @CreatedDate
    @Column(name = "appealed_at", nullable = false)
    private Instant appealedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        CaseEntity that = (CaseEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
