package com.hiveapp.platform.client.role.domain.repository;
import com.hiveapp.platform.client.role.domain.entity.Role;
import com.hiveapp.platform.client.role.domain.constant.RoleStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.UUID;
import java.util.List;
import java.util.Optional;
public interface RoleRepository extends JpaRepository<Role, UUID> {

    /**
     * Read model for the role list surfaces. The graph loads each role's permission rows and
     * their permission definitions up front, because {@code RoleMapper} projects permission
     * codes and would otherwise issue one statement per role.
     */
    @EntityGraph(attributePaths = {"permissions", "permissions.permission"})
    List<Role> findAllByAccountId(UUID accountId);
    long countByAccountIdAndIsSystemRoleFalseAndStatus(UUID accountId, RoleStatus status);

    /** Company-scoped counterpart of {@link #findAllByAccountId(UUID)}; same projection needs. */
    @EntityGraph(attributePaths = {"permissions", "permissions.permission"})
    List<Role> findAllByBoundaryCompanyId(UUID companyId);
    /**
     * Role detail read model. Both callers project permission codes, so the graph loads them
     * with the role instead of leaving the mapper to walk lazy collections under open-in-view.
     */
    @EntityGraph(attributePaths = {"permissions", "permissions.permission"})
    Optional<Role> findByIdAndAccountId(UUID id, UUID accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT role FROM Role role WHERE role.id = :id AND role.account.id = :accountId")
    Optional<Role> findByIdAndAccountIdForUpdate(@Param("id") UUID id, @Param("accountId") UUID accountId);
}
