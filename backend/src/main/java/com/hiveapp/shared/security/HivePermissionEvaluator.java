package com.hiveapp.shared.security;

import com.hiveapp.permission.engine.PermissionResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.UUID;

/**
 * Custom Spring Security PermissionEvaluator that bridges @PreAuthorize annotations
 * to the HiveApp PermissionResolver engine.
 *
 * Usage in controllers:
 *   @PreAuthorize("hasPermission(#companyId, 'COMPANY', 'invoices.read')")
 *
 * This evaluator:
 * 1. Extracts the authenticated member context
 * 2. Calls PermissionResolver.hasPermission() with the real permission engine
 * 3. Returns true/false to Spring Security
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HivePermissionEvaluator implements PermissionEvaluator {

    private final PermissionResolver permissionResolver;

    /**
     * Called by @PreAuthorize("hasPermission(targetId, targetType, permission)")
     *
     * @param authentication The current authentication (contains HiveAppUserDetails)
     * @param targetId       The target object ID (e.g., companyId as UUID)
     * @param targetType     The target type (e.g., "COMPANY", "COLLABORATION")
     * @param permission     The permission code to check (e.g., "invoices.read")
     */
    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId,
                                  String targetType, Object permission) {
        if (authentication == null || !(authentication.getPrincipal() instanceof HiveAppUserDetails userDetails)) {
            return false;
        }

        String permissionCode = permission.toString();
        UUID userId = userDetails.getUserId();

        // Extract memberId and accountId from security context
        // These would be set during authentication based on the active account/member
        UUID memberId = userDetails.getMemberId();
        UUID accountId = userDetails.getAccountId();

        if (memberId == null || accountId == null) {
            log.warn("Member or account context not available for user {}", userId);
            return false;
        }

        try {
            if ("COMPANY".equalsIgnoreCase(targetType)) {
                UUID companyId = UUID.fromString(targetId.toString());
                return permissionResolver.hasPermission(memberId, accountId, companyId, permissionCode);
            }

            if ("COLLABORATION".equalsIgnoreCase(targetType)) {
                UUID collaborationId = UUID.fromString(targetId.toString());
                return permissionResolver.hasCollaborationPermission(
                        memberId, accountId, collaborationId, permissionCode);
            }

            log.warn("Unknown target type: {}", targetType);
            return false;

        } catch (Exception e) {
            log.error("Permission evaluation failed for user {} on {} {}: {}",
                    userId, targetType, targetId, e.getMessage());
            return false;
        }
    }

    /**
     * Called by @PreAuthorize("hasPermission(targetObject, permission)")
     * Not used in our architecture — we use the ID-based version above.
     */
    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        log.warn("Object-based hasPermission not supported — use ID-based version");
        return false;
    }
}
