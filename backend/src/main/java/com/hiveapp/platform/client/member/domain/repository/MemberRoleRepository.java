package com.hiveapp.platform.client.member.domain.repository;

import com.hiveapp.platform.client.member.domain.entity.MemberRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;
import java.util.List;

public interface MemberRoleRepository extends JpaRepository<MemberRole, UUID> {
    List<MemberRole> findAllByMemberId(UUID memberId);
    List<MemberRole> findAllByCompanyId(UUID companyId);
    List<MemberRole> findAllByRoleId(UUID roleId);

    boolean existsByMemberIdAndRoleIdAndCompanyId(UUID memberId, UUID roleId, UUID companyId);

    boolean existsByMemberIdAndRoleIdAndCompanyIsNull(UUID memberId, UUID roleId);

    @Query("SELECT COUNT(mr) > 0 FROM MemberRole mr JOIN mr.role r JOIN r.permissions rp JOIN rp.permission p " +
           "WHERE mr.member.id = :memberId AND p.code = :permissionCode " +
           "AND r.status = com.hiveapp.platform.client.role.domain.constant.RoleStatus.ACTIVE " +
           "AND (mr.company.id = :companyId OR mr.company IS NULL)")
    boolean existsByMemberIdAndPermissionCode(UUID memberId, String permissionCode, UUID companyId);

    @Modifying
    @Query("DELETE FROM MemberRole mr WHERE mr.member.id = :memberId AND mr.role.id = :roleId")
    void deleteByMemberIdAndRoleId(@Param("memberId") UUID memberId, @Param("roleId") UUID roleId);
}
