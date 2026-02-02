package com.hiveapp.role.domain.repository;

import com.hiveapp.role.domain.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Set;
import java.util.UUID;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, UUID> {

    @Query("SELECT rp.permissionId FROM RolePermission rp WHERE rp.role.id = :roleId")
    Set<UUID> findPermissionIdsByRoleId(@Param("roleId") UUID roleId);

    @Query("SELECT rp.permissionId FROM RolePermission rp WHERE rp.role.id IN :roleIds")
    Set<UUID> findPermissionIdsByRoleIds(@Param("roleIds") Set<UUID> roleIds);

    void deleteByRoleIdAndPermissionId(UUID roleId, UUID permissionId);
}
