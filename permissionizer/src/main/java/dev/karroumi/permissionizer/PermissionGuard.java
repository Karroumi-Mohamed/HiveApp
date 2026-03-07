package dev.karroumi.permissionizer;

import java.util.Collection;

/**
 * Runtime permission evaluator using prefix matching.
 *
 * <p>Before use, the host application must call {@link #configure(PermissionsProvider)}
 * once at startup to supply the current user's permissions.</p>
 *
 * <p>Prefix matching means a user holding "erp.hr" passes a check for
 * "erp.hr.payroll.export" because "erp.hr" is a proper ancestor in the
 * permission tree. Matching respects dot boundaries — "erp.hr" does NOT
 * match "erp.hra.something".</p>
 *
 * <h3>Usage:</h3>
 * <pre>
 * // At startup — once
 * PermissionGuard.configure(myProvider);
 *
 * // In service methods — uses configured provider
 * PermissionGuard.check(PlatformPermissions.Client.Account.Operations.Create.$);
 *
 * // Boolean variant
 * if (PermissionGuard.has(PlatformPermissions.Client.Account.$)) { ... }
 *
 * // Explicit authorities — no provider needed
 * PermissionGuard.check("some.permission", userAuthorities);
 * </pre>
 */
public final class PermissionGuard {

    private static volatile PermissionsProvider provider;

    private PermissionGuard() {}

    /**
     * Configures the global permissions provider. Must be called once at
     * application startup before any permission checks are performed.
     *
     * @param provider the provider that supplies current user permissions
     * @throws IllegalArgumentException if provider is null
     */
    public static void configure(PermissionsProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("PermissionsProvider must not be null");
        }
        PermissionGuard.provider = provider;
    }

    // ──────────────────────────────────────────────
    // Provider-based methods
    // ──────────────────────────────────────────────

    /**
     * Checks if the current user has the required permission.
     * Uses the configured {@link PermissionsProvider}.
     *
     * @param requiredPermission the full dot-path to check
     * @throws SecurityException if the user lacks the permission
     * @throws IllegalStateException if no provider has been configured
     */
    public static void check(String requiredPermission) {
        check(requiredPermission, getProviderPermissions());
    }

    /**
     * Returns true if the current user has the required permission.
     * Uses the configured {@link PermissionsProvider}.
     *
     * @param requiredPermission the full dot-path to check
     * @return true if granted, false otherwise
     */
    public static boolean has(String requiredPermission) {
        PermissionsProvider p = provider;
        if (p == null) {
            return false;
        }
        Collection<String> permissions = p.getPermissions();
        if (permissions == null) {
            return false;
        }
        return matches(requiredPermission, permissions);
    }

    // ──────────────────────────────────────────────
    // Explicit authority methods
    // ──────────────────────────────────────────────

    /**
     * Checks if the given authorities grant the required permission.
     * No provider needed — pass the authorities directly.
     *
     * @param requiredPermission the full dot-path to check
     * @param authorities the user's granted permission strings
     * @throws SecurityException if the permission is not granted
     */
    public static void check(String requiredPermission, Collection<String> authorities) {
        if (authorities == null || !matches(requiredPermission, authorities)) {
            throw new SecurityException("Required permission: " + requiredPermission);
        }
    }

    /**
     * Returns true if the given authorities grant the required permission.
     *
     * @param requiredPermission the full dot-path to check
     * @param authorities the user's granted permission strings
     * @return true if granted, false otherwise
     */
    public static boolean has(String requiredPermission, Collection<String> authorities) {
        if (authorities == null) {
            return false;
        }
        return matches(requiredPermission, authorities);
    }

    // ──────────────────────────────────────────────
    // Core matching
    // ──────────────────────────────────────────────

    /**
     * Tests whether any granted authority covers the required permission.
     *
     * <p>An authority matches if it equals the required permission exactly,
     * or if the required permission starts with the authority followed by a dot.
     * The dot boundary prevents "erp.hr" from matching "erp.hra.something".</p>
     */
    private static boolean matches(String required, Collection<String> authorities) {
        for (String granted : authorities) {
            if (required.equals(granted)) {
                return true;
            }
            if (required.startsWith(granted + ".")) {
                return true;
            }
        }
        return false;
    }

    private static Collection<String> getProviderPermissions() {
        PermissionsProvider p = provider;
        if (p == null) {
            throw new IllegalStateException(
                "PermissionGuard not configured. " +
                "Call PermissionGuard.configure(provider) at application startup."
            );
        }
        Collection<String> permissions = p.getPermissions();
        if (permissions == null) {
            throw new SecurityException("PermissionsProvider returned null");
        }
        return permissions;
    }
}
