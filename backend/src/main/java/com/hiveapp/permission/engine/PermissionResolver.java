package com.hiveapp.permission.engine;

import com.hiveapp.collaboration.domain.entity.Collaboration;
import com.hiveapp.collaboration.domain.entity.CollaborationPermission;
import com.hiveapp.collaboration.domain.repository.CollaborationRepository;
import com.hiveapp.company.domain.repository.CompanyModuleRepository;
import com.hiveapp.member.domain.entity.Member;
import com.hiveapp.member.domain.repository.MemberRepository;
import com.hiveapp.member.domain.repository.MemberRoleRepository;
import com.hiveapp.permission.domain.entity.Permission;
import com.hiveapp.permission.domain.repository.PermissionRepository;
import com.hiveapp.plan.domain.entity.Plan;
import com.hiveapp.plan.domain.entity.PlanFeature;
import com.hiveapp.plan.domain.service.PlanService;
import com.hiveapp.role.domain.repository.RolePermissionRepository;
import com.hiveapp.shared.exception.BusinessException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import com.hiveapp.subscription.domain.entity.Subscription;
import com.hiveapp.subscription.domain.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Permission Resolution Orchestrator.
 *
 * This is the high-level service that wires together the Permission Engine
 * with real data from the database. It resolves effective permissions for a
 * member in any of the three contexts:
 *
 * 1. CLIENT_OWN_ACCOUNT: member operates within their own account
 *    Formula: rolePermissions ∩ planCeiling ∩ companyModulePermissions
 *
 * 2. CLIENT_COLLABORATION: member operates on a company via collaboration
 *    Formula: rolePermissions ∩ collaborationCeiling ∩ companyModulePermissions
 *
 * 3. ADMIN_PLATFORM: admin platform (handled separately via AdminService)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PermissionResolver {

    private final PermissionEngine permissionEngine;
    private final PermissionRepository permissionRepository;
    private final MemberRepository memberRepository;
    private final MemberRoleRepository memberRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanService planService;
    private final CompanyModuleRepository companyModuleRepository;
    private final CollaborationRepository collaborationRepository;

    /**
     * Resolve effective permissions for a member operating on their own account's company.
     *
     * Flow:
     * 1. Get the member's role IDs (account-wide + company-scoped)
     * 2. Get all permission IDs from those roles (union)
     * 3. Build rolePermissions PermissionSet
     * 4. Build planCeiling PermissionSet from the account's active plan
     * 5. Build companyModulePermissions from the company's active modules
     * 6. Apply formula: rolePermissions ∩ planCeiling ∩ companyModulePermissions
     */
    @Cacheable(value = "permissions", key = "'own:' + #memberId + ':' + #accountId + ':' + #companyId")
    public PermissionSet resolveForOwnAccount(UUID memberId, UUID accountId, UUID companyId) {
        log.debug("Resolving permissions for member {} on own account {} company {}",
                memberId, accountId, companyId);

        // Guard: deactivated members have no permissions
        if (!isMemberActive(memberId)) {
            log.debug("Member {} is deactivated — returning empty permissions", memberId);
            return PermissionSet.empty();
        }

        // Step 1: Get member's role IDs (account-wide roles + company-scoped roles)
        Set<UUID> roleIds = memberRoleRepository.findRoleIdsForMemberAndCompany(memberId, companyId);

        if (roleIds.isEmpty()) {
            log.debug("Member {} has no roles for company {} — returning empty permissions", memberId, companyId);
            return PermissionSet.empty();
        }

        // Step 2: Get all permission IDs from those roles (union across all roles)
        Set<UUID> permissionIds = rolePermissionRepository.findPermissionIdsByRoleIds(roleIds);

        if (permissionIds.isEmpty()) {
            return PermissionSet.empty();
        }

        // Step 3: Build rolePermissions set
        List<Permission> rolePermissionsList = permissionRepository.findByIds(permissionIds);
        PermissionSet rolePermissions = PermissionSet.of(rolePermissionsList);

        // Step 4: Build plan ceiling
        PermissionSet planCeiling = buildPlanCeiling(accountId);

        // Step 5: Build company module permissions
        PermissionSet companyModulePermissions = buildCompanyModulePermissions(companyId);

        // Step 6: Apply formula
        PermissionSet effective = permissionEngine.resolveWithCompanyFilter(
                rolePermissions, planCeiling, companyModulePermissions);

        log.debug("Resolved {} effective permissions for member {} on company {} (from {} role permissions, {} plan ceiling, {} module permissions)",
                effective.size(), memberId, companyId,
                rolePermissions.size(), planCeiling.size(), companyModulePermissions.size());

        return effective;
    }

    /**
     * Resolve effective permissions for a member operating via collaboration
     * (provider member accessing client's company).
     *
     * Flow:
     * 1. Get the collaboration and its permission ceiling
     * 2. Get the member's role IDs in the provider account
     * 3. Build rolePermissions from those roles
     * 4. Build collaborationCeiling from the collaboration's permission set
     * 5. Build companyModulePermissions from the target company's active modules
     * 6. Apply formula: rolePermissions ∩ collaborationCeiling ∩ companyModulePermissions
     */
    @Cacheable(value = "collaborationCeilings", key = "'collab:' + #memberId + ':' + #collaborationId")
    public PermissionSet resolveForCollaboration(UUID memberId, UUID providerAccountId, UUID collaborationId) {
        log.debug("Resolving collaboration permissions for member {} via collaboration {}",
                memberId, collaborationId);

        // Guard: deactivated members have no permissions
        if (!isMemberActive(memberId)) {
            log.debug("Member {} is deactivated — returning empty permissions", memberId);
            return PermissionSet.empty();
        }

        // Step 1: Get the collaboration
        Collaboration collaboration = collaborationRepository.findByIdWithPermissions(collaborationId)
                .orElseThrow(() -> new ResourceNotFoundException("Collaboration", "id", collaborationId));

        if (!"active".equals(collaboration.getStatus())) {
            log.debug("Collaboration {} is not active (status: {}) — returning empty permissions",
                    collaborationId, collaboration.getStatus());
            return PermissionSet.empty();
        }

        UUID companyId = collaboration.getCompanyId();

        // Step 2: Get member's role IDs in the provider account (account-wide roles only for collaboration)
        Set<UUID> roleIds = memberRoleRepository.findAllRoleIdsByMemberId(memberId);

        if (roleIds.isEmpty()) {
            return PermissionSet.empty();
        }

        // Step 3: Build rolePermissions set
        Set<UUID> permissionIds = rolePermissionRepository.findPermissionIdsByRoleIds(roleIds);
        if (permissionIds.isEmpty()) {
            return PermissionSet.empty();
        }
        List<Permission> rolePermissionsList = permissionRepository.findByIds(permissionIds);
        PermissionSet rolePermissions = PermissionSet.of(rolePermissionsList);

        // Step 4: Build collaboration ceiling
        Set<UUID> collabPermissionIds = collaboration.getCollaborationPermissions().stream()
                .map(CollaborationPermission::getPermissionId)
                .collect(Collectors.toSet());

        PermissionSet collaborationCeiling;
        if (collabPermissionIds.isEmpty()) {
            collaborationCeiling = PermissionSet.empty();
        } else {
            List<Permission> collabPermissions = permissionRepository.findByIds(collabPermissionIds);
            collaborationCeiling = PermissionSet.of(collabPermissions);
        }

        // Step 5: Build company module permissions
        PermissionSet companyModulePermissions = buildCompanyModulePermissions(companyId);

        // Step 6: Apply formula
        PermissionSet effective = permissionEngine.resolveWithCompanyFilter(
                rolePermissions, collaborationCeiling, companyModulePermissions);

        log.debug("Resolved {} effective collaboration permissions for member {} on collaboration {} (from {} role, {} ceiling, {} module)",
                effective.size(), memberId, collaborationId,
                rolePermissions.size(), collaborationCeiling.size(), companyModulePermissions.size());

        return effective;
    }

    /**
     * Check if a member has a specific permission on their own account's company.
     */
    public boolean hasPermission(UUID memberId, UUID accountId, UUID companyId, String permissionCode) {
        PermissionSet effective = resolveForOwnAccount(memberId, accountId, companyId);
        return effective.has(permissionCode);
    }

    /**
     * Check if a member has a specific permission via collaboration.
     */
    public boolean hasCollaborationPermission(UUID memberId, UUID providerAccountId,
                                               UUID collaborationId, String permissionCode) {
        PermissionSet effective = resolveForCollaboration(memberId, providerAccountId, collaborationId);
        return effective.has(permissionCode);
    }

    // ---- Private helpers ----

    private boolean isMemberActive(UUID memberId) {
        return memberRepository.findById(memberId)
                .map(Member::isActorActive)
                .orElse(false);
    }

    /**
     * Build the plan ceiling: all permissions whose features are included in the account's active plan.
     */
    @Cacheable(value = "planCeilings", key = "'plan:' + #accountId")
    public PermissionSet buildPlanCeiling(UUID accountId) {
        Subscription subscription = subscriptionRepository
                .findByAccountIdAndStatus(accountId, "active")
                .orElse(null);

        if (subscription == null) {
            log.warn("No active subscription for account {} — plan ceiling is empty", accountId);
            return PermissionSet.empty();
        }

        Plan plan = planService.findPlanOrThrow(subscription.getPlanId());

        Set<UUID> planFeatureIds = plan.getPlanFeatures().stream()
                .map(PlanFeature::getFeatureId)
                .collect(Collectors.toSet());

        if (planFeatureIds.isEmpty()) {
            log.warn("Plan {} has no features — ceiling is empty", plan.getId());
            return PermissionSet.empty();
        }

        List<Permission> ceilingPermissions = permissionRepository.findByFeatureIds(planFeatureIds);
        PermissionSet ceiling = PermissionSet.of(ceilingPermissions);

        log.debug("Built plan ceiling for account {}: {} permissions from {} features",
                accountId, ceiling.size(), planFeatureIds.size());

        return ceiling;
    }

    /**
     * Build the company module permissions: all permissions from features of the company's active modules.
     */
    private PermissionSet buildCompanyModulePermissions(UUID companyId) {
        Set<UUID> activeModuleIds = companyModuleRepository.findActiveModuleIdsByCompanyId(companyId);

        if (activeModuleIds.isEmpty()) {
            log.debug("Company {} has no active modules — returning empty permissions", companyId);
            return PermissionSet.empty();
        }

        // Get all features for active modules, then all permissions for those features
        // For now, we use module-level filtering. The features belong to modules,
        // and permissions belong to features. We need to go: activeModuleIds → featureIds → permissions.
        // This requires a query through the module→feature→permission chain.
        // Since Permission has featureId and Feature has moduleId, we need a join query.
        // For efficiency, we use a dedicated repository method.
        List<Permission> modulePermissions = permissionRepository.findByModuleIds(activeModuleIds);
        PermissionSet result = PermissionSet.of(modulePermissions);

        log.debug("Built company module permissions for company {}: {} permissions from {} modules",
                companyId, result.size(), activeModuleIds.size());

        return result;
    }
}
