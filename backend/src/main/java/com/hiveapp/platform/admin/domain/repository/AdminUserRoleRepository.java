package com.hiveapp.platform.admin.domain.repository;

import com.hiveapp.platform.admin.domain.entity.AdminUserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface AdminUserRoleRepository extends JpaRepository<AdminUserRole, UUID> {

    boolean existsByAdminUserIdAndAdminRoleId(UUID adminUserId, UUID adminRoleId);

    @Modifying
    @Query("DELETE FROM AdminUserRole aur WHERE aur.adminUser.id = :adminUserId AND aur.adminRole.id = :adminRoleId")
    void deleteByAdminUserIdAndAdminRoleId(@Param("adminUserId") UUID adminUserId,
                                           @Param("adminRoleId") UUID adminRoleId);
}
