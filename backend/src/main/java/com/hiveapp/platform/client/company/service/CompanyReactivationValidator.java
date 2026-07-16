package com.hiveapp.platform.client.company.service;

import com.hiveapp.platform.client.account.domain.entity.Company;
import com.hiveapp.platform.client.collaboration.domain.constant.CollaborationStatus;
import com.hiveapp.platform.client.collaboration.domain.repository.CollaborationPermissionRepository;
import com.hiveapp.platform.client.member.domain.repository.MemberPermissionOverrideRepository;
import com.hiveapp.platform.client.member.domain.repository.MemberRoleRepository;
import com.hiveapp.platform.client.plan.service.PlanEntitlementService;
import com.hiveapp.shared.exception.InvalidStateException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CompanyReactivationValidator {

    private final MemberRoleRepository memberRoleRepository;
    private final MemberPermissionOverrideRepository memberOverrideRepository;
    private final CollaborationPermissionRepository collaborationPermissionRepository;
    private final PlanEntitlementService planEntitlementService;

    public void validate(Company company) {
        Set<String> permissionsToRestore = new LinkedHashSet<>();

        memberRoleRepository.findAllByCompanyId(company.getId()).stream()
                .filter(assignment -> assignment.getMember().isActive())
                .filter(assignment -> assignment.getRole().isActive())
                .flatMap(assignment -> assignment.getRole().getPermissions().stream())
                .map(rolePermission -> rolePermission.getPermission().getCode())
                .forEach(permissionsToRestore::add);

        memberOverrideRepository.findAllByCompanyId(company.getId()).stream()
                .filter(override -> override.getMember().isActive())
                .filter(override -> override.isDecision())
                .map(override -> override.getPermission().getCode())
                .forEach(permissionsToRestore::add);

        collaborationPermissionRepository
                .findAllByCollaborationCompanyIdAndCollaborationStatus(
                        company.getId(), CollaborationStatus.ACTIVE)
                .stream()
                .map(permission -> permission.getPermission().getCode())
                .forEach(permissionsToRestore::add);

        var unavailable = permissionsToRestore.stream()
                .filter(permission -> !planEntitlementService.isPermissionEntitled(
                        company.getAccount().getId(), permission))
                .sorted()
                .toList();
        if (!unavailable.isEmpty()) {
            throw new InvalidStateException(
                    "Company cannot be reactivated until unavailable saved permissions are removed: "
                            + String.join(", ", unavailable));
        }
    }
}
