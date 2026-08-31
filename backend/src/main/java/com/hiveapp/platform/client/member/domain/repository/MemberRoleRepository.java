package com.hiveapp.platform.client.member.domain.repository;

import com.hiveapp.platform.client.member.domain.entity.MemberRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;
import java.util.List;

public interface MemberRoleRepository extends JpaRepository<MemberRole, UUID> {
    List<MemberRole> findAllByMemberId(UUID memberId);

    @EntityGraph(attributePaths = {"role", "scopeCompany"})
    @Query("SELECT assignment FROM MemberRole assignment WHERE assignment.member.id = :memberId")
    List<MemberRole> findAllForAuthorizationByMemberId(@Param("memberId") UUID memberId);
    List<MemberRole> findAllByScopeCompanyId(UUID companyId);
    List<MemberRole> findAllByRoleId(UUID roleId);

    /**
     * Impact-preview read model. Only that caller projects member state
     * ({@code getMember().isActive()}), so the member join lives here rather than on
     * {@link #findAllByRoleId(UUID)}, whose other callers just count assignments.
     */
    @EntityGraph(attributePaths = {"role", "scopeCompany", "member"})
    List<MemberRole> findWithMemberByRoleId(UUID roleId);

    boolean existsByMemberIdAndRoleIdAndScopeCompanyId(UUID memberId, UUID roleId, UUID companyId);

    boolean existsByMemberIdAndRoleIdAndScopeCompanyIsNull(UUID memberId, UUID roleId);

    @Query("SELECT COUNT(mr) > 0 FROM MemberRole mr JOIN mr.role r JOIN r.permissions rp JOIN rp.permission p " +
           "WHERE mr.member.id = :memberId AND p.code = :permissionCode " +
           "AND r.status = com.hiveapp.platform.client.role.domain.constant.RoleStatus.ACTIVE " +
           "AND (mr.scopeCompany.id = :companyId OR mr.scopeCompany IS NULL)")
    boolean existsByMemberIdAndPermissionCode(UUID memberId, String permissionCode, UUID companyId);

    @Modifying
    @Query("DELETE FROM MemberRole mr WHERE mr.member.id = :memberId AND mr.role.id = :roleId AND mr.scopeCompany IS NULL")
    int deleteAccountAssignment(@Param("memberId") UUID memberId, @Param("roleId") UUID roleId);

    @Modifying
    @Query("DELETE FROM MemberRole mr WHERE mr.member.id = :memberId AND mr.role.id = :roleId AND mr.scopeCompany.id = :companyId")
    int deleteCompanyAssignment(@Param("memberId") UUID memberId, @Param("roleId") UUID roleId,
                                @Param("companyId") UUID companyId);
}
