package com.hiveapp.platform.registry.domain.repository;
import com.hiveapp.platform.registry.domain.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.Optional;
public interface PermissionRepository extends JpaRepository<Permission, UUID> {
    Optional<Permission> findByCode(String code);
}
