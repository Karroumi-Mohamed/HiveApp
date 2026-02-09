package com.hiveapp.role.domain.listener;

import com.hiveapp.permission.domain.entity.Permission;
import com.hiveapp.permission.domain.repository.PermissionRepository;
import com.hiveapp.plan.domain.entity.Plan;
import com.hiveapp.plan.domain.entity.PlanFeature;
import com.hiveapp.plan.domain.service.PlanService;
import com.hiveapp.plan.event.PlanChangedEvent;
import com.hiveapp.role.domain.entity.Role;
import com.hiveapp.role.domain.entity.RolePermission;
import com.hiveapp.role.domain.repository.RoleRepository;
import com.hiveapp.role.event.RolePermissionsChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * On plan downgrade, strips out-of-plan permissions from all roles in the account.
 *
 * Spec rule: "Lors d'un downgrade, les Permissions hors-plan sont retirées des Roles"
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlanDowngradeRoleListener {

    private final RoleRepository roleRepository;
    private final PlanService planService;
    private final PermissionRepository permissionRepository;
    private final ApplicationEventPublisher eventPublisher;

    @EventListener
    @Transactional
    public void onPlanChanged(PlanChangedEvent event) {
        UUID accountId = event.getAccountId();
        UUID newPlanId = event.getNewPlanId();

        Plan newPlan = planService.findPlanOrThrow(newPlanId);
        Set<UUID> newPlanFeatureIds = newPlan.getPlanFeatures().stream()
                .map(PlanFeature::getFeatureId)
                .collect(Collectors.toSet());

        List<Role> roles = roleRepository.findByAccountIdWithPermissions(accountId);

        for (Role role : roles) {
            boolean modified = false;
            Iterator<RolePermission> it = role.getRolePermissions().iterator();

            while (it.hasNext()) {
                RolePermission rp = it.next();
                Permission permission = permissionRepository.findById(rp.getPermissionId()).orElse(null);
                if (permission != null && !newPlanFeatureIds.contains(permission.getFeatureId())) {
                    it.remove();
                    modified = true;
                }
            }

            if (modified) {
                roleRepository.save(role);
                eventPublisher.publishEvent(new RolePermissionsChangedEvent(role.getId(), accountId));
                log.info("Role '{}' stripped of out-of-plan permissions after plan downgrade (account={})",
                        role.getName(), accountId);
            }
        }
    }
}
