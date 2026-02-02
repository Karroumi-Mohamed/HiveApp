package com.hiveapp.member.domain.repository;

import com.hiveapp.member.domain.entity.MemberRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public interface MemberRoleRepository extends JpaRepository<MemberRole, UUID> {

    List<MemberRole> findByMemberId(UUID memberId);

    @Query("SELECT mr FROM MemberRole mr WHERE mr.member.id = :memberId AND (mr.companyId IS NULL OR mr.companyId = :companyId)")
    List<MemberRole> findByMemberIdForCompany(@Param("memberId") UUID memberId, @Param("companyId") UUID companyId);

    @Query("SELECT mr FROM MemberRole mr WHERE mr.member.id = :memberId AND mr.companyId IS NULL")
    List<MemberRole> findAccountWideByMemberId(@Param("memberId") UUID memberId);

    @Query("SELECT DISTINCT mr.roleId FROM MemberRole mr WHERE mr.member.id = :memberId AND (mr.companyId IS NULL OR mr.companyId = :companyId)")
    Set<UUID> findRoleIdsForMemberAndCompany(@Param("memberId") UUID memberId, @Param("companyId") UUID companyId);

    @Query("SELECT DISTINCT mr.roleId FROM MemberRole mr WHERE mr.member.id = :memberId")
    Set<UUID> findAllRoleIdsByMemberId(@Param("memberId") UUID memberId);

    @Query("SELECT DISTINCT mr.companyId FROM MemberRole mr WHERE mr.member.id = :memberId AND mr.companyId IS NOT NULL")
    Set<UUID> findAccessibleCompanyIdsByMemberId(@Param("memberId") UUID memberId);

    void deleteByMemberIdAndRoleIdAndCompanyId(UUID memberId, UUID roleId, UUID companyId);
}
