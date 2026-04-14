package com.hiveapp.platform.registry.domain.repository;

import com.hiveapp.platform.registry.domain.entity.Module;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.Optional;
import java.util.List;
import java.util.Set;

public interface ModuleRepository extends JpaRepository<Module, UUID> {
    Optional<Module> findByCode(String code);
    List<Module> findAllByCodeIn(Set<String> codes);
}
