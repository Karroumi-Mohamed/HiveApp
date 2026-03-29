package com.hiveapp.platform.admin.domain.repository;
import com.hiveapp.platform.admin.domain.entity.AdminPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.Optional;
public interface AdminPermissionRepository extends JpaRepository<AdminPermission, UUID> {
    Optional<AdminPermission> findByCode(String code);
}
