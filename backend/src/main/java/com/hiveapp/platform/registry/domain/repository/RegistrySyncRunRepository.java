package com.hiveapp.platform.registry.domain.repository;

import com.hiveapp.platform.registry.domain.entity.RegistrySyncRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RegistrySyncRunRepository extends JpaRepository<RegistrySyncRun, UUID> {
    Optional<RegistrySyncRun> findFirstByOrderByStartedAtDesc();
}
