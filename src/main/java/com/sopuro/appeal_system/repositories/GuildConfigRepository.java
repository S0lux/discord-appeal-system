package com.sopuro.appeal_system.repositories;

import com.sopuro.appeal_system.entities.GuildConfigEntity;
import com.sopuro.appeal_system.entities.GuildConfigId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuildConfigRepository extends JpaRepository<GuildConfigEntity, GuildConfigId> {
}
