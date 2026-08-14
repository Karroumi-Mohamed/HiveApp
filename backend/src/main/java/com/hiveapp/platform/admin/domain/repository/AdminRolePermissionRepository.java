package com.hiveapp.platform.admin.domain.repository;

import com.hiveapp.platform.admin.domain.entity.AdminRolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;
import java.util.List;
import java.util.Collection;

public interface AdminRolePermissionRepository extends JpaRepository<AdminRolePermission, UUID> {

    boolean existsByAdminRoleIdAndPermissionId(UUID adminRoleId, UUID permissionId);

    List<AdminRolePermission> findAllByAdminRoleId(UUID adminRoleId);

    @EntityGraph(attributePaths = {"adminRole", "permission"})
    List<AdminRolePermission> findAllWithPermissionByAdminRoleId(UUID adminRoleId);

    @EntityGraph(attributePaths = {"adminRole", "permission"})
    @Query("SELECT grant FROM AdminRolePermission grant " +
           "WHERE grant.adminRole.id IN :adminRoleIds")
    List<AdminRolePermission> findAllWithPermissionByAdminRoleIdIn(
            @Param("adminRoleIds") Collection<UUID> adminRoleIds);

    @Modifying
    @Query("DELETE FROM AdminRolePermission arp WHERE arp.adminRole.id = :adminRoleId AND arp.permission.id = :permissionId")
    void deleteByAdminRoleIdAndPermissionId(@Param("adminRoleId") UUID adminRoleId,
                                            @Param("permissionId") UUID permissionId);

    @Modifying
    @Query("DELETE FROM AdminRolePermission arp WHERE arp.adminRole.id = :adminRoleId")
    void deleteAllByAdminRoleId(@Param("adminRoleId") UUID adminRoleId);
}
