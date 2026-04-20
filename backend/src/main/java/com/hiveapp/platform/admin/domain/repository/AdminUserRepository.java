package com.hiveapp.platform.admin.domain.repository;
import com.hiveapp.platform.admin.domain.entity.AdminUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;
import java.util.Optional;

public interface AdminUserRepository extends JpaRepository<AdminUser, UUID> {
    Optional<AdminUser> findByUserId(UUID userId);
    Optional<AdminUser> findByUser_Email(String email);

    /**
     * Returns true if the admin user has the given permission code via any
     * of their assigned active AdminRoles.
     *
     * Traversal: AdminUser → AdminUserRole → AdminRole → AdminRolePermission → AdminPermission.code
     */
    @Query("SELECT COUNT(aur) > 0 FROM AdminUserRole aur, AdminRolePermission arp " +
           "WHERE aur.adminUser.id = :adminUserId " +
           "AND arp.adminRole = aur.adminRole " +
           "AND arp.adminPermission.code = :permissionCode " +
           "AND aur.adminRole.isActive = true")
    boolean hasPermission(@Param("adminUserId") UUID adminUserId,
                          @Param("permissionCode") String permissionCode);
}
