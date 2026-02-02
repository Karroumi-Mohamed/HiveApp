package com.hiveapp.role.domain.service;

import com.hiveapp.permission.domain.entity.Permission;
import com.hiveapp.permission.domain.repository.PermissionRepository;
import com.hiveapp.plan.domain.entity.Plan;
import com.hiveapp.plan.domain.entity.PlanFeature;
import com.hiveapp.plan.domain.service.PlanService;
import com.hiveapp.role.domain.dto.*;
import com.hiveapp.role.domain.entity.Role;
import com.hiveapp.role.domain.entity.RolePermission;
import com.hiveapp.role.domain.mapper.RoleMapper;
import com.hiveapp.role.domain.repository.RolePermissionRepository;
import com.hiveapp.role.domain.repository.RoleRepository;
import com.hiveapp.role.event.RolePermissionsChangedEvent;
import com.hiveapp.shared.exception.BusinessException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import com.hiveapp.subscription.domain.entity.Subscription;
import com.hiveapp.subscription.domain.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoleService {

    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final RoleMapper roleMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanService planService;
    private final PermissionRepository permissionRepository;

    @Transactional
    public RoleResponse createRole(CreateRoleRequest request) {
        Role role = Role.builder()
                .accountId(request.getAccountId())
                .name(request.getName())
                .description(request.getDescription())
                .build();

        if (request.getPermissionIds() != null && !request.getPermissionIds().isEmpty()) {
            validatePermissionsWithinPlan(request.getAccountId(), request.getPermissionIds());

            for (UUID permissionId : request.getPermissionIds()) {
                RolePermission rp = RolePermission.builder()
                        .permissionId(permissionId)
                        .build();
                role.addPermission(rp);
            }
        }

        Role saved = roleRepository.save(role);
        log.info("Role created: {} in account {}", saved.getName(), saved.getAccountId());
        return roleMapper.toResponse(saved);
    }

    @Transactional
    public RoleResponse updateRole(UUID id, UpdateRoleRequest request) {
        Role role = roleRepository.findByIdWithPermissions(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "id", id));

        if (role.isSystemRole()) {
            throw new BusinessException("Cannot modify system role");
        }

        if (request.getName() != null) role.setName(request.getName());
        if (request.getDescription() != null) role.setDescription(request.getDescription());

        if (request.getPermissionIds() != null) {
            validatePermissionsWithinPlan(role.getAccountId(), request.getPermissionIds());

            role.clearPermissions();
            for (UUID permissionId : request.getPermissionIds()) {
                RolePermission rp = RolePermission.builder()
                        .permissionId(permissionId)
                        .build();
                role.addPermission(rp);
            }
            eventPublisher.publishEvent(new RolePermissionsChangedEvent(role.getId(), role.getAccountId()));
        }

        Role saved = roleRepository.save(role);
        log.info("Role updated: {}", saved.getId());
        return roleMapper.toResponse(saved);
    }

    @Transactional
    public void deleteRole(UUID id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "id", id));

        if (role.isSystemRole()) {
            throw new BusinessException("Cannot delete system role");
        }

        role.deactivate();
        roleRepository.save(role);
        log.info("Role deactivated: {}", id);
    }

    public RoleResponse getRoleById(UUID id) {
        Role role = roleRepository.findByIdWithPermissions(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "id", id));
        return roleMapper.toResponse(role);
    }

    public List<RoleResponse> getRolesByAccountId(UUID accountId) {
        return roleMapper.toResponseList(roleRepository.findByAccountIdWithPermissions(accountId));
    }

    public Set<UUID> getPermissionIdsForRoles(Set<UUID> roleIds) {
        return rolePermissionRepository.findPermissionIdsByRoleIds(roleIds);
    }

    public Role findRoleOrThrow(UUID id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "id", id));
    }

    /**
     * Validates that all requested permission IDs belong to features
     * covered by the Account's active Plan.
     *
     * Spec rule: "Un Role ne peut contenir que des Permissions couvertes par le Plan de l'Account"
     */
    private void validatePermissionsWithinPlan(UUID accountId, Collection<UUID> permissionIds) {
        Subscription subscription = subscriptionRepository
                .findByAccountIdAndStatus(accountId, "active")
                .orElseThrow(() -> new BusinessException("No active subscription found for account"));

        Plan plan = planService.findPlanOrThrow(subscription.getPlanId());

        Set<UUID> planFeatureIds = plan.getPlanFeatures().stream()
                .map(PlanFeature::getFeatureId)
                .collect(Collectors.toSet());

        List<Permission> permissions = permissionRepository.findByIds(new HashSet<>(permissionIds));
        Set<UUID> outOfPlanFeatures = permissions.stream()
                .map(Permission::getFeatureId)
                .filter(fid -> !planFeatureIds.contains(fid))
                .collect(Collectors.toSet());

        if (!outOfPlanFeatures.isEmpty()) {
            throw new BusinessException(
                    "Role contains permissions from features not included in the account's plan. " +
                    "Remove out-of-plan permissions or upgrade the plan.");
        }
    }
}
