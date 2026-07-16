package com.hiveapp.platform.client.member.service;

import com.hiveapp.platform.client.member.domain.entity.Member;
import com.hiveapp.platform.client.member.dto.MemberPermissionOverrideDto;
import com.hiveapp.platform.client.member.dto.CreateMemberRequest;
import com.hiveapp.platform.client.member.dto.MemberAccessResponse;
import com.hiveapp.platform.client.member.dto.MemberCreationResult;

import java.util.List;
import java.util.UUID;

public interface MemberService {
    Member getMember(UUID id);
    List<Member> getAccountMembers(UUID accountId);
    MemberCreationResult createMember(UUID accountId, CreateMemberRequest request);
    Member updateMember(UUID memberId, String displayName);
    void deactivateMember(UUID id);
    MemberAccessResponse regenerateInitialAccess(UUID memberId);
    MemberAccessResponse resetAccess(UUID memberId);
    void unlockInitialAccess(UUID memberId);

    void assignRole(UUID memberId, UUID roleId, UUID companyId);
    void removeRole(UUID memberId, UUID roleId);

    void grantPermissionOverride(UUID memberId, String permissionCode, UUID companyId, boolean decision);
    void revokePermissionOverride(UUID memberId, String permissionCode, UUID companyId);
    List<MemberPermissionOverrideDto> getMemberOverrides(UUID memberId, UUID companyId);
}
