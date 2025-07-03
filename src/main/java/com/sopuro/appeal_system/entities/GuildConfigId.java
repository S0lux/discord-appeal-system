package com.sopuro.appeal_system.entities;

import com.sopuro.appeal_system.shared.enums.GuildConfig;
import org.hibernate.proxy.HibernateProxy;

import java.io.Serializable;
import java.util.Objects;

public class GuildConfigId implements Serializable {
    private String guildId;
    private GuildConfig configKey;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        GuildConfigId that = (GuildConfigId) o;
        return guildId != null && Objects.equals(guildId, that.guildId)
                && configKey != null && Objects.equals(configKey, that.configKey);
    }

    @Override
    public final int hashCode() {
        return Objects.hash(guildId, configKey);
    }
}
