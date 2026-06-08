package com.hiveapp.platform.admin.domain.repository;

import com.hiveapp.platform.admin.domain.entity.AdminRolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;
import java.util.List;

public interface AdminRolePermissionRepository extends JpaRepository<AdminRolePermission, UUID> {

    boolean existsByAdminRoleIdAndPermissionId(UUID adminRoleId, UUID permissionId);

    List<AdminRolePermission> findAllByAdminRoleId(UUID adminRoleId);

    @Modifying
    @Query("DELETE FROM AdminRolePermission arp WHERE arp.adminRole.id = :adminRoleId AND arp.permission.id = :permissionId")
    void deleteByAdminRoleIdAndPermissionId(@Param("adminRoleId") UUID adminRoleId,
                                            @Param("permissionId") UUID permissionId);
}
