package com.hiveapp.shared.security;

import com.hiveapp.platform.client.member.domain.entity.MemberRole;
import com.hiveapp.platform.client.member.domain.repository.MemberPermissionOverrideRepository;
import com.hiveapp.platform.client.member.domain.repository.MemberRepository;
import com.hiveapp.platform.client.member.domain.repository.MemberRoleRepository;
import com.hiveapp.platform.client.member.dto.MemberPermissionDto;
import com.hiveapp.platform.client.plan.service.PlanEntitlementService;
import com.hiveapp.platform.client.role.domain.entity.RolePermission;
import com.hiveapp.platform.registry.definition.PermissionGrantValidator;
import com.hiveapp.platform.registry.domain.repository.PermissionRepository;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.time.Instant;
import com.hiveapp.platform.client.member.domain.constant.PermissionOverrideDecision;

@Service
@RequiredArgsConstructor
public class EffectivePermissionService {

    private final MemberRepository memberRepository;
    private final MemberRoleRepository memberRoleRepository;
    private final MemberPermissionOverrideRepository memberOverrideRepository;
    private final PermissionRepository permissionRepository;
    private final PermissionGrantValidator permissionGrantValidator;
    private final PlanEntitlementService planEntitlementService;

    @Transactional(readOnly = true)
    public MemberPermissionDto getEffectivePermissions(UUID userId, UUID accountId) {
        return getEffectivePermissions(userId, accountId, null);
    }

    @Transactional(readOnly = true)
    public MemberPermissionDto getEffectivePermissions(UUID userId, UUID accountId, UUID targetCompanyId) {
        var member = memberRepository.findByAccountIdAndUserId(accountId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Member", "userId", userId));

        if (member.isOwner()) {
            var all = permissionRepository.findAll()
                    .stream()
                    .filter(permissionGrantValidator::isClientRoleGrantable)
                    .filter(p -> planEntitlementService.isPermissionEntitled(accountId, p.getCode()))
                    .map(p -> p.getCode())
                    .collect(Collectors.toSet());
            return new MemberPermissionDto(member.getId(), true, all);
        }

        Set<String> permissions = new HashSet<>();
        for (MemberRole mr : memberRoleRepository.findAllByMemberId(member.getId())) {
            if (!mr.getRole().isActive()) {
                continue;
            }
            if (mr.getScopeCompany() != null
                    && (!mr.getScopeCompany().isActive()
                    || !mr.getScopeCompany().getId().equals(targetCompanyId))) {
                continue;
            }
            for (RolePermission rp : mr.getRole().getPermissions()) {
                permissions.add(rp.getPermission().getCode());
            }
        }

        Set<String> denied = new HashSet<>();
        Instant now = Instant.now();
        for (var override : memberOverrideRepository.findApplicable(member.getId(), targetCompanyId)) {
            if (!override.isEffectiveAt(now)) {
                continue;
            }
            if (override.getScopeCompany() != null && !override.getScopeCompany().isActive()) {
                continue;
            }
            if (override.getDecision() == PermissionOverrideDecision.GRANT) {
                permissions.add(override.getPermission().getCode());
            } else {
                denied.add(override.getPermission().getCode());
            }
        }
        permissions.removeAll(denied);

        permissions.removeIf(permissionCode -> !planEntitlementService.isPermissionEntitled(accountId, permissionCode));
        return new MemberPermissionDto(member.getId(), false, permissions);
    }
}
