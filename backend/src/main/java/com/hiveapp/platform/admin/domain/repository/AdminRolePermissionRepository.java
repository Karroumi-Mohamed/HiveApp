package com.hiveapp.platform.admin.domain.repository;

import com.hiveapp.platform.admin.domain.entity.AdminRolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface AdminRolePermissionRepository extends JpaRepository<AdminRolePermission, UUID> {

    boolean existsByAdminRoleIdAndAdminPermissionId(UUID adminRoleId, UUID adminPermissionId);

    @Modifying
    @Query("DELETE FROM AdminRolePermission arp WHERE arp.adminRole.id = :adminRoleId AND arp.adminPermission.id = :adminPermissionId")
    void deleteByAdminRoleIdAndAdminPermissionId(@Param("adminRoleId") UUID adminRoleId,
                                                 @Param("adminPermissionId") UUID adminPermissionId);
}
