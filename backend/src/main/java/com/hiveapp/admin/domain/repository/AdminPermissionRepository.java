package com.hiveapp.admin.domain.repository;

import com.hiveapp.admin.domain.entity.AdminPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdminPermissionRepository extends JpaRepository<AdminPermission, UUID> {

    Optional<AdminPermission> findByCode(String code);

    boolean existsByCode(String code);

    List<AdminPermission> findByModuleId(UUID moduleId);

    List<AdminPermission> findByActionAndResource(String action, String resource);
}
