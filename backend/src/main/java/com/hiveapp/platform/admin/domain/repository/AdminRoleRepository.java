package com.hiveapp.platform.admin.domain.repository;
import com.hiveapp.platform.admin.domain.constant.AdminRoleStatus;
import com.hiveapp.platform.admin.domain.entity.AdminRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import java.util.UUID;
import java.util.Optional;

public interface AdminRoleRepository extends JpaRepository<AdminRole, UUID> {
    long countByStatus(AdminRoleStatus status);

    default long countByIsActiveTrue() {
        return countByStatus(AdminRoleStatus.ACTIVE);
    }

    default long countByIsActiveFalse() {
        return count() - countByStatus(AdminRoleStatus.ACTIVE);
    }

    Optional<AdminRole> findByNormalizedName(String normalizedName);
    boolean existsByNormalizedName(String normalizedName);
    boolean existsByNormalizedNameAndIdNot(String normalizedName, UUID id);

    /**
     * Advances the impact-preview version without optimistic-locking concurrent assignments
     * against one another. Permission/status writes still see the version change and fail stale.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AdminRole role set role.everAssigned = true, "
            + "role.assignmentRevision = role.assignmentRevision + 1, "
            + "role.updatedByUserId = :actorUserId, role.updatedAt = :updatedAt, "
            + "role.version = role.version + 1 "
            + "where role.id = :roleId")
    int advanceAssignmentRevision(
            @Param("roleId") UUID roleId,
            @Param("actorUserId") UUID actorUserId,
            @Param("updatedAt") java.time.Instant updatedAt);

    @Query("select role from AdminRole role where "
            + "((:status is null and role.status <> com.hiveapp.platform.admin.domain.constant.AdminRoleStatus.ARCHIVED) "
            + "or role.status = :status) "
            + "and (:search is null or lower(role.name) like lower(concat('%', :search, '%')) "
            + "or lower(coalesce(role.description, '')) like lower(concat('%', :search, '%')))")
    Page<AdminRole> search(
            @Param("search") String search,
            @Param("status") AdminRoleStatus status,
            Pageable pageable);

    @Query(value = "select role from AdminRole role "
            + "left join AdminUserRole assignment on assignment.adminRole = role "
            + "where ((:status is null and role.status <> "
            + "com.hiveapp.platform.admin.domain.constant.AdminRoleStatus.ARCHIVED) "
            + "or role.status = :status) "
            + "and (:search is null or lower(role.name) like lower(concat('%', :search, '%')) "
            + "or lower(coalesce(role.description, '')) like lower(concat('%', :search, '%'))) "
            + "group by role order by count(assignment) asc",
            countQuery = "select count(role) from AdminRole role where "
                    + "((:status is null and role.status <> "
                    + "com.hiveapp.platform.admin.domain.constant.AdminRoleStatus.ARCHIVED) "
                    + "or role.status = :status) "
                    + "and (:search is null or lower(role.name) like lower(concat('%', :search, '%')) "
                    + "or lower(coalesce(role.description, '')) like lower(concat('%', :search, '%')))")
    Page<AdminRole> searchOrderByAssignmentCountAsc(
            @Param("search") String search,
            @Param("status") AdminRoleStatus status,
            Pageable pageable);

    @Query(value = "select role from AdminRole role "
            + "left join AdminUserRole assignment on assignment.adminRole = role "
            + "where ((:status is null and role.status <> "
            + "com.hiveapp.platform.admin.domain.constant.AdminRoleStatus.ARCHIVED) "
            + "or role.status = :status) "
            + "and (:search is null or lower(role.name) like lower(concat('%', :search, '%')) "
            + "or lower(coalesce(role.description, '')) like lower(concat('%', :search, '%'))) "
            + "group by role order by count(assignment) desc",
            countQuery = "select count(role) from AdminRole role where "
                    + "((:status is null and role.status <> "
                    + "com.hiveapp.platform.admin.domain.constant.AdminRoleStatus.ARCHIVED) "
                    + "or role.status = :status) "
                    + "and (:search is null or lower(role.name) like lower(concat('%', :search, '%')) "
                    + "or lower(coalesce(role.description, '')) like lower(concat('%', :search, '%')))")
    Page<AdminRole> searchOrderByAssignmentCountDesc(
            @Param("search") String search,
            @Param("status") AdminRoleStatus status,
            Pageable pageable);
}
