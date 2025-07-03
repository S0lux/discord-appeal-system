package com.sopuro.appeal_system.entities;

import com.sopuro.appeal_system.shared.enums.GuildConfig;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;

import java.util.Objects;

@Entity
@Table(name = "guild_configs")
@IdClass(GuildConfigId.class)
@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GuildConfigEntity {
    @Id
    @Column(name = "guild_id")
    private String guildId;

    @Id
    @Column(name = "config_key", nullable = false)
    @Enumerated(EnumType.STRING)
    private GuildConfig configKey;

    @Column(name = "config_value", nullable = false)
    private String configValue;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        GuildConfigEntity that = (GuildConfigEntity) o;
        return getGuildId() != null && Objects.equals(getGuildId(), that.getGuildId())
                && getConfigKey() != null && Objects.equals(getConfigKey(), that.getConfigKey());
    }

    @Override
    public final int hashCode() {
        return Objects.hash(guildId, configKey);
    }
}
