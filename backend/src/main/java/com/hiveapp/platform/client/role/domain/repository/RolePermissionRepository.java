package com.hiveapp.platform.client.role.domain.repository;
import com.hiveapp.platform.client.role.domain.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RolePermissionRepository extends JpaRepository<RolePermission, UUID> {
    @Query("SELECT CASE WHEN COUNT(rp) > 0 THEN true ELSE false END FROM RolePermission rp WHERE rp.role.id = :roleId AND rp.permission.code = :permissionCode")
    boolean existsByRoleIdAndPermissionCode(@Param("roleId") UUID roleId, @Param("permissionCode") String permissionCode);
}
