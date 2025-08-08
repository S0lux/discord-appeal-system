package com.sopuro.appeal_system.repositories;

import com.sopuro.appeal_system.entities.GuildConfigEntity;
import com.sopuro.appeal_system.entities.GuildConfigId;
import com.sopuro.appeal_system.shared.enums.GuildConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.concurrent.Callable;

public interface GuildConfigRepository extends JpaRepository<GuildConfigEntity, GuildConfigId> {
    Optional<GuildConfigEntity> findByGuildIdAndConfigKey(String guildId, GuildConfig configKey);
}
