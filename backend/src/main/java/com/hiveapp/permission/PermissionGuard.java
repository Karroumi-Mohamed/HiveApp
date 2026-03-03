package com.hiveapp.permission;

import java.util.Collection;

/**
 * Runtime permission evaluator using prefix matching.
 *
 * <p>Provides both a Spring Security-aware version that reads authorities
 * from {@code SecurityContextHolder}, and a pure-Java version that accepts
 * authorities as a parameter.</p>
 *
 * <p>Prefix matching means that a user holding "erp.hr" will pass a check
 * for "erp.hr.payroll.export", because "erp.hr" is a prefix of the required path.
 * This enables hierarchical cascading — granting a branch grants all leaves beneath it.</p>
 *
 * <h3>Usage:</h3>
 * <pre>
 * // Spring-aware — reads from SecurityContextHolder
 * PermissionGuard.check(PayrollServicePermissions.EXPORT);
 *
 * // Pure-Java — pass authorities explicitly
 * PermissionGuard.check(PayrollServicePermissions.EXPORT, userAuthorities);
 *
 * // Boolean variants — no exception, returns true/false
 * if (PermissionGuard.has(PayrollServicePermissions.EXPORT)) { ... }
 * </pre>
 */
public final class PermissionGuard {

    private PermissionGuard() {}

    // ──────────────────────────────────────────────
    // Spring Security-aware methods
    // ──────────────────────────────────────────────

    /**
     * Checks if the current authenticated user has the required permission.
     * Reads authorities from Spring Security's {@code SecurityContextHolder}.
     *
     * @param requiredPermission the full dot-path permission to check
     * @throws org.springframework.security.access.AccessDeniedException
     *         if the user lacks the required permission
     * @throws org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
     *         if no authenticated user is present
     */
    public static void check(String requiredPermission) {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()) {
            throw new org.springframework.security.authentication
                    .AuthenticationCredentialsNotFoundException(
                    "No authenticated user in security context"
            );
        }

        boolean granted = auth.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .anyMatch(authority -> matchesPermission(requiredPermission, authority));

        if (!granted) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Required permission: " + requiredPermission
            );
        }
    }

    /**
     * Checks if the current authenticated user has the required permission.
     * Returns a boolean instead of throwing.
     *
     * @param requiredPermission the full dot-path permission to check
     * @return true if the user has the permission, false otherwise
     */
    public static boolean has(String requiredPermission) {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }

        return auth.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .anyMatch(authority -> matchesPermission(requiredPermission, authority));
    }

    // ──────────────────────────────────────────────
    // Pure-Java methods (no Spring dependency)
    // ──────────────────────────────────────────────

    /**
     * Checks if the given set of authorities grants the required permission.
     * No Spring dependency — pass the authorities directly.
     *
     * @param requiredPermission the full dot-path permission to check
     * @param authorities the user's granted authority strings
     * @throws SecurityException if the user lacks the required permission
     */
    public static void check(String requiredPermission, Collection<String> authorities) {
        boolean granted = authorities.stream()
                .anyMatch(authority -> matchesPermission(requiredPermission, authority));

        if (!granted) {
            throw new SecurityException("Required permission: " + requiredPermission);
        }
    }

    /**
     * Checks if the given set of authorities grants the required permission.
     * Returns a boolean instead of throwing.
     *
     * @param requiredPermission the full dot-path permission to check
     * @param authorities the user's granted authority strings
     * @return true if any authority grants the permission, false otherwise
     */
    public static boolean has(String requiredPermission, Collection<String> authorities) {
        return authorities.stream()
                .anyMatch(authority -> matchesPermission(requiredPermission, authority));
    }

    // ──────────────────────────────────────────────
    // Core matching logic
    // ──────────────────────────────────────────────

    /**
     * Determines if a granted authority covers the required permission
     * via prefix matching.
     *
     * <p>The authority matches if:</p>
     * <ul>
     *   <li>It equals the required permission exactly, OR</li>
     *   <li>The required permission starts with the authority followed by a dot</li>
     * </ul>
     *
     * <p>The dot boundary check prevents "erp.hr" from matching "erp.hra.something".
     * Only exact matches and proper hierarchy boundaries are accepted.</p>
     *
     * @param required the permission being checked (e.g., "erp.hr.payroll.export")
     * @param granted  the authority the user holds (e.g., "erp.hr")
     * @return true if the granted authority covers the required permission
     */
    private static boolean matchesPermission(String required, String granted) {
        if (required.equals(granted)) {
            return true;
        }
        return required.startsWith(granted + ".");
    }
}