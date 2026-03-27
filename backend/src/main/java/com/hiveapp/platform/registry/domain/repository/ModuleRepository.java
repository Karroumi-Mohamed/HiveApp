package com.hiveapp.platform.registry.domain.repository;
import com.hiveapp.platform.registry.domain.entity.Module;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface ModuleRepository extends JpaRepository<Module, UUID> {}
