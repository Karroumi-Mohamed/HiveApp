package com.hiveapp.platform.admin.service;

import com.hiveapp.platform.admin.domain.repository.AdminUserRoleRepository;
import com.hiveapp.platform.admin.dto.AdminRoleSummaryDto;
import com.hiveapp.platform.admin.dto.AdminUserCreationResponse;
import com.hiveapp.platform.admin.dto.AdminUserResponseDto;
import com.hiveapp.platform.admin.dto.CreateAdminUserRequest;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates reviewed access as one transaction before activation delivery can run. */
@Service
@RequiredArgsConstructor
public class AdminOperatorProvisioningService {
    private final AdminUserService adminUserService;
    private final AdminUserRoleRepository adminUserRoleRepository;

    @Transactional
    public AdminUserCreationResponse create(CreateAdminUserRequest request) {
        // These calls cross the existing guarded service proxy. Creating an operator does
        // not grant role-assignment authority or widen the acting administrator's ceiling.
        AdminUserCreationResponse created = adminUserService.createAdminUser(
                request.firstName(), request.lastName(), request.email(),
                request.initialAccessMethod(), request.isSuperAdmin());
        var requestedRoles = request.roleIds() == null
                ? List.<java.util.UUID>of()
                : request.roleIds();
        for (var roleId : new LinkedHashSet<>(requestedRoles)) {
            adminUserService.assignRole(created.operator().id(), roleId);
        }
        if (requestedRoles.isEmpty()) {
            return created;
        }
        // CredentialEmailListener runs AFTER_COMMIT. Any invalid/denied assignment rolls
        // back the identity, credentials and operator as well as all assignments, and no
        // activation invitation is sent. Reading the response does not require read_detail.
        var roles = adminUserRoleRepository
                .findAllWithRoleByAdminUserIdIn(List.of(created.operator().id())).stream()
                .map(assignment -> assignment.getAdminRole())
                .map(role -> new AdminRoleSummaryDto(
                        role.getId(), role.getName(), role.getDescription(),
                        role.getStatus(), role.isActive()))
                .toList();
        var operator = created.operator();
        var response = new AdminUserResponseDto(
                operator.id(), operator.userId(), operator.email(), operator.firstName(),
                operator.lastName(), operator.emailVerified(), operator.isSuperAdmin(),
                operator.isActive(), operator.credentialState(), roles);
        return new AdminUserCreationResponse(
                response, created.initialAccessMethod(), created.temporaryPassword(),
                created.linkExpiresAt(), created.credentialState(), created.emailDeliveryId(),
                created.emailDelivery());
    }
}
