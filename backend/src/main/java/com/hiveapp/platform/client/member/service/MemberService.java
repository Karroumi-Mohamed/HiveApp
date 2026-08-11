package com.hiveapp.platform.client.member.service;

import com.hiveapp.platform.client.member.dto.MemberPermissionOverrideDto;
import com.hiveapp.platform.client.member.dto.CreateMemberRequest;
import com.hiveapp.platform.client.member.dto.MemberAccessResponse;
import com.hiveapp.platform.client.member.dto.MemberAccessResult;
import com.hiveapp.platform.client.member.dto.MemberAccessStatusResponse;
import com.hiveapp.platform.client.member.dto.MemberCreationResult;
import com.hiveapp.platform.client.member.dto.MemberAuthorizationDto;
import com.hiveapp.platform.client.member.dto.MemberDto;
import com.hiveapp.platform.client.member.domain.constant.RoleAssignmentScope;
import com.hiveapp.platform.client.member.domain.constant.PermissionOverrideDecision;
import com.hiveapp.platform.client.member.domain.constant.PermissionOverrideScope;
import java.time.Instant;

import java.util.List;
import java.util.UUID;

public interface MemberService {
    List<MemberDto> getAccountMembers(UUID accountId);
    MemberCreationResult createMember(UUID accountId, CreateMemberRequest request);
    MemberDto updateMember(UUID memberId, String displayName);
    void deactivateMember(UUID id);
    MemberAccessResult regenerateInitialAccess(UUID memberId);
    MemberAccessResult resetAccess(UUID memberId);
    MemberAccessStatusResponse getAccessStatus(UUID memberId);
    MemberAuthorizationDto getMemberAuthorization(UUID memberId);
    void unlockInitialAccess(UUID memberId);

    void assignRole(UUID memberId, UUID roleId, RoleAssignmentScope scope, UUID companyId);
    void removeRole(UUID memberId, UUID roleId, RoleAssignmentScope scope, UUID companyId);

    void grantPermissionOverride(UUID memberId, String permissionCode, PermissionOverrideScope scope,
                                 UUID companyId, PermissionOverrideDecision decision,
                                 String reason, Instant expiresAt);
    void revokePermissionOverride(UUID memberId, String permissionCode,
                                  PermissionOverrideScope scope, UUID companyId);
    List<MemberPermissionOverrideDto> getMemberOverrides(
            UUID memberId, PermissionOverrideScope scope, UUID companyId);
}
