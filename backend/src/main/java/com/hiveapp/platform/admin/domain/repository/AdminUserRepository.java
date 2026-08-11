package com.hiveapp.platform.admin.domain.repository;
import com.hiveapp.platform.admin.domain.entity.AdminUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface AdminUserRepository extends JpaRepository<AdminUser, UUID> {
    Optional<AdminUser> findByUserId(UUID userId);
    Optional<AdminUser> findByUser_Email(String email);

    @EntityGraph(attributePaths = "user")
    @Query(
            value = "SELECT admin FROM AdminUser admin",
            countQuery = "SELECT COUNT(admin) FROM AdminUser admin")
    Page<AdminUser> findPageWithUser(Pageable pageable);

    @EntityGraph(attributePaths = "user")
    @Query("SELECT admin FROM AdminUser admin WHERE admin.id = :id")
    Optional<AdminUser> findWithUserById(@Param("id") UUID id);

    /**
     * Returns true if the admin user has the given permission code via any
     * of their assigned active AdminRoles.
     *
     * Traversal: AdminUser → AdminUserRole → AdminRole → AdminRolePermission → Permission.code
     */
    @Query("SELECT COUNT(aur) > 0 FROM AdminUserRole aur, AdminRolePermission arp " +
           "WHERE aur.adminUser.id = :adminUserId " +
           "AND arp.adminRole = aur.adminRole " +
           "AND arp.permission.code = :permissionCode " +
           "AND aur.adminRole.isActive = true")
    boolean hasPermission(@Param("adminUserId") UUID adminUserId,
                          @Param("permissionCode") String permissionCode);

    @Query("SELECT arp.permission.code FROM AdminUserRole aur " +
           "JOIN AdminRolePermission arp ON arp.adminRole = aur.adminRole " +
           "WHERE aur.adminUser.id = :adminUserId AND aur.adminRole.isActive = true")
    List<String> findAllPermissionCodes(@Param("adminUserId") UUID adminUserId);
}
