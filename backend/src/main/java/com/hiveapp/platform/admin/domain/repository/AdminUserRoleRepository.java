package com.hiveapp.platform.admin.domain.repository;

import com.hiveapp.platform.admin.domain.entity.AdminUserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;
import java.util.List;
import java.util.Collection;

public interface AdminUserRoleRepository extends JpaRepository<AdminUserRole, UUID> {
    List<AdminUserRole> findAllByAdminUserId(UUID adminUserId);

    @Query("SELECT assignment.adminRole.id FROM AdminUserRole assignment "
           + "WHERE assignment.adminUser.id = :adminUserId")
    List<UUID> findAllRoleIdsByAdminUserId(@Param("adminUserId") UUID adminUserId);

    @EntityGraph(attributePaths = {"adminUser", "adminRole"})
    @Query("SELECT assignment FROM AdminUserRole assignment " +
           "WHERE assignment.adminUser.id IN :adminUserIds")
    List<AdminUserRole> findAllWithRoleByAdminUserIdIn(
            @Param("adminUserIds") Collection<UUID> adminUserIds);

    /** How many operators hold each role, batched so a page of roles costs one query. */
    @Query("SELECT assignment.adminRole.id AS roleId, COUNT(assignment) AS total "
           + "FROM AdminUserRole assignment WHERE assignment.adminRole.id IN :roleIds "
           + "GROUP BY assignment.adminRole.id")
    List<RoleAssignmentCount> countByAdminRoleIdIn(@Param("roleIds") Collection<UUID> roleIds);

    interface RoleAssignmentCount {
        UUID getRoleId();

        long getTotal();
    }

    /** Operators holding a role, with the identity fetched so rendering costs no extra query. */
    @EntityGraph(attributePaths = {"adminUser", "adminUser.user"})
    @Query("SELECT assignment FROM AdminUserRole assignment WHERE assignment.adminRole.id = :roleId")
    List<AdminUserRole> findAllWithOperatorByAdminRoleId(@Param("roleId") UUID roleId);

    boolean existsByAdminUserIdAndAdminRoleId(UUID adminUserId, UUID adminRoleId);

    boolean existsByAdminRoleId(UUID adminRoleId);

    long countByAdminRoleId(UUID adminRoleId);

    @Query("SELECT assignment.adminUser.id FROM AdminUserRole assignment "
           + "WHERE assignment.adminRole.id = :roleId")
    List<UUID> findAllAdminUserIdsByAdminRoleId(@Param("roleId") UUID roleId);

    @Modifying
    @Query("DELETE FROM AdminUserRole aur WHERE aur.adminUser.id = :adminUserId AND aur.adminRole.id = :adminRoleId")
    void deleteByAdminUserIdAndAdminRoleId(@Param("adminUserId") UUID adminUserId,
                                           @Param("adminRoleId") UUID adminRoleId);
}
