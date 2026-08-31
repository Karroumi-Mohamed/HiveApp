package com.hiveapp.platform.registry.domain.repository;
import com.hiveapp.platform.registry.domain.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.Optional;
import java.util.Collection;
import java.util.List;
public interface PermissionRepository extends JpaRepository<Permission, UUID> {
    Optional<Permission> findByCode(String code);
    List<Permission> findAllByCodeIn(Collection<String> codes);
}
