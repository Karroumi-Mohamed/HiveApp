package com.hiveapp.platform.client.role.domain.repository;
import com.hiveapp.platform.client.role.domain.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface RolePermissionRepository extends JpaRepository<RolePermission, UUID> {
    @Query("SELECT CASE WHEN COUNT(rp) > 0 THEN true ELSE false END FROM RolePermission rp WHERE rp.role.id = :roleId AND rp.permission.code = :permissionCode")
    boolean existsByRoleIdAndPermissionCode(@Param("roleId") UUID roleId, @Param("permissionCode") String permissionCode);

    @Modifying
    @Query("DELETE FROM RolePermission rp WHERE rp.role.id = :roleId AND rp.permission.code = :permissionCode")
    void deleteByRoleIdAndPermissionCode(@Param("roleId") UUID roleId, @Param("permissionCode") String permissionCode);
}
