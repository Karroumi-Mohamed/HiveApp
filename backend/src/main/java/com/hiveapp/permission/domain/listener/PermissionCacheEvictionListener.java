package com.hiveapp.permission.domain.listener;

import com.hiveapp.collaboration.event.CollaborationPermissionsChangedEvent;
import com.hiveapp.member.event.MemberRolesChangedEvent;
import com.hiveapp.plan.event.PlanChangedEvent;
import com.hiveapp.plan.event.PlanFeaturesChangedEvent;
import com.hiveapp.role.event.RolePermissionsChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Central listener that evicts permission caches when underlying data changes.
 *
 * Listens for:
 * - RolePermissionsChangedEvent → evicts permission cache for the account
 * - MemberRolesChangedEvent → evicts permission cache for the member
 * - CollaborationPermissionsChangedEvent → evicts collaboration ceiling cache
 * - PlanChangedEvent → evicts plan ceiling cache for the account
 * - PlanFeaturesChangedEvent → evicts plan ceiling + permission caches when features added/removed
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionCacheEvictionListener {

    private final CacheManager cacheManager;

    @EventListener
    public void onRolePermissionsChanged(RolePermissionsChangedEvent event) {
        log.info("Role permissions changed for role {} in account {} — evicting permission caches",
                event.getRoleId(), event.getAccountId());
        evictCache("permissions");
    }

    @EventListener
    public void onMemberRolesChanged(MemberRolesChangedEvent event) {
        log.info("Member roles changed for member {} in account {} — evicting permission caches",
                event.getMemberId(), event.getAccountId());
        evictCache("permissions");
    }

    @EventListener
    public void onCollaborationPermissionsChanged(CollaborationPermissionsChangedEvent event) {
        log.info("Collaboration permissions changed for collaboration {} (provider: {}) — evicting collaboration caches",
                event.getCollaborationId(), event.getProviderAccountId());
        evictCache("collaborationCeilings");
    }

    @EventListener
    public void onPlanChanged(PlanChangedEvent event) {
        log.info("Plan changed for account {} (old: {} → new: {}) — evicting plan ceiling caches",
                event.getAccountId(), event.getOldPlanId(), event.getNewPlanId());
        evictCache("planCeilings");
        evictCache("permissions");
    }

    @EventListener
    public void onPlanFeaturesChanged(PlanFeaturesChangedEvent event) {
        log.info("Plan features changed for plan {} — evicting plan ceiling and permission caches",
                event.getPlanId());
        evictCache("planCeilings");
        evictCache("permissions");
    }

    private void evictCache(String cacheName) {
        try {
            Objects.requireNonNull(cacheManager.getCache(cacheName)).clear();
            log.debug("Cache '{}' evicted successfully", cacheName);
        } catch (Exception e) {
            log.warn("Failed to evict cache '{}': {}", cacheName, e.getMessage());
        }
    }
}
