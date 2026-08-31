package com.hiveapp.platform.client.member.domain.repository;
import com.hiveapp.platform.client.member.domain.entity.MemberPermissionOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.UUID;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberPermissionOverrideRepository extends JpaRepository<MemberPermissionOverride, UUID> {
    @EntityGraph(attributePaths = {"permission", "scopeCompany", "createdBy"})
    @Query("SELECT exception FROM MemberPermissionOverride exception " +
           "WHERE exception.member.id = :memberId")
    List<MemberPermissionOverride> findAllForAuthorizationByMemberId(
            @Param("memberId") UUID memberId);

    List<MemberPermissionOverride> findAllByScopeCompanyId(UUID companyId);
    List<MemberPermissionOverride> findAllByMemberIdAndScopeCompanyId(UUID memberId, UUID companyId);
    List<MemberPermissionOverride> findAllByMemberIdAndScopeCompanyIsNull(UUID memberId);
    Optional<MemberPermissionOverride> findByMemberIdAndScopeCompanyIdAndPermissionId(
            UUID memberId, UUID companyId, UUID permissionId);
    Optional<MemberPermissionOverride> findByMemberIdAndScopeCompanyIsNullAndPermissionId(
            UUID memberId, UUID permissionId);

    @Query("SELECT exception FROM MemberPermissionOverride exception " +
           "WHERE exception.member.id = :memberId AND (" +
           "exception.scope = com.hiveapp.platform.client.member.domain.constant.PermissionOverrideScope.ACCOUNT " +
           "OR (:companyId IS NOT NULL AND exception.scope = " +
           "com.hiveapp.platform.client.member.domain.constant.PermissionOverrideScope.COMPANY " +
           "AND exception.scopeCompany.id = :companyId))")
    List<MemberPermissionOverride> findApplicable(
            @Param("memberId") UUID memberId,
            @Param("companyId") UUID companyId);
}
