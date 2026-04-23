package com.hiveapp.platform.client.member.service;

import com.hiveapp.platform.client.member.domain.entity.Member;
import com.hiveapp.platform.client.member.domain.entity.MemberPermissionOverride;

import java.util.List;
import java.util.UUID;

public interface MemberService {
    Member getMember(UUID id);
    List<Member> getAccountMembers(UUID accountId);
    Member addMember(UUID accountId, UUID userId, String displayName);
    Member updateMember(UUID memberId, String displayName);
    void deactivateMember(UUID id);

    void assignRole(UUID memberId, UUID roleId, UUID companyId);
    void removeRole(UUID memberId, UUID roleId);

    void grantPermissionOverride(UUID memberId, String permissionCode, UUID companyId, boolean decision);
    void revokePermissionOverride(UUID memberId, String permissionCode, UUID companyId);
    List<MemberPermissionOverride> getMemberOverrides(UUID memberId, UUID companyId);
}
