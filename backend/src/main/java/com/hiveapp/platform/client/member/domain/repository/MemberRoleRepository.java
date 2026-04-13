package com.hiveapp.platform.client.member.domain.repository;

import com.hiveapp.platform.client.member.domain.entity.MemberRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.UUID;
import java.util.List;

public interface MemberRoleRepository extends JpaRepository<MemberRole, UUID> {
    List<MemberRole> findAllByMemberId(UUID memberId);

    @Query("SELECT COUNT(mr) > 0 FROM MemberRole mr JOIN mr.role r JOIN r.permissions rp JOIN rp.permission p " +
           "WHERE mr.member.id = :memberId AND p.code = :permissionCode " +
           "AND (mr.company.id = :companyId OR mr.company IS NULL)")
    boolean existsByMemberIdAndPermissionCode(UUID memberId, String permissionCode, UUID companyId);
}
