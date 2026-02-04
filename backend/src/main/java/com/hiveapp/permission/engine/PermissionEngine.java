package com.hiveapp.permission.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Core Permission Engine — shared across Admin and Client platforms.
 *
 * Resolves effective permissions by:
 * 1. Gathering role permissions (union of all roles)
 * 2. Determining the applicable ceiling (Plan or Collaboration)
 * 3. Applying intersection: rolePermissions ∩ ceiling
 * 4. Optionally filtering by active company modules
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionEngine {

    /**
     * Resolve effective permissions for a given set of role permissions and a ceiling.
     *
     * @param rolePermissions Union of all permissions from the actor's roles
     * @param ceiling         The applicable ceiling (plan-based or collaboration-based)
     * @return The effective PermissionSet after applying the ceiling
     */
    public PermissionSet resolve(PermissionSet rolePermissions, PermissionSet ceiling) {
        if (rolePermissions.isEmpty()) {
            return PermissionSet.empty();
        }
        if (ceiling.isEmpty()) {
            return PermissionSet.empty();
        }
        return rolePermissions.intersect(ceiling);
    }

    /**
     * Resolve effective permissions with an additional company module filter.
     *
     * @param rolePermissions   Union of all permissions from the actor's roles
     * @param ceiling           The applicable ceiling
     * @param companyModulePermissions Permissions available through active company modules
     * @return The effective PermissionSet
     */
    public PermissionSet resolveWithCompanyFilter(
            PermissionSet rolePermissions,
            PermissionSet ceiling,
            PermissionSet companyModulePermissions
    ) {
        PermissionSet afterCeiling = resolve(rolePermissions, ceiling);
        if (companyModulePermissions.isEmpty()) {
            return PermissionSet.empty();
        }
        return afterCeiling.intersect(companyModulePermissions);
    }

    /**
     * Check if a specific permission is granted after resolution.
     */
    public boolean checkAccess(
            PermissionSet rolePermissions,
            PermissionSet ceiling,
            String permissionCode
    ) {
        PermissionSet effective = resolve(rolePermissions, ceiling);
        return effective.has(permissionCode);
    }

    /**
     * Check access with company module filter.
     */
    public boolean checkAccessWithCompanyFilter(
            PermissionSet rolePermissions,
            PermissionSet ceiling,
            PermissionSet companyModulePermissions,
            String permissionCode
    ) {
        PermissionSet effective = resolveWithCompanyFilter(rolePermissions, ceiling, companyModulePermissions);
        return effective.has(permissionCode);
    }

    /**
     * Merge permissions from multiple roles into a single set (union).
     */
    public PermissionSet mergeRolePermissions(PermissionSet... roleSets) {
        PermissionSet result = PermissionSet.empty();
        for (PermissionSet set : roleSets) {
            result = result.union(set);
        }
        return result;
    }
}
