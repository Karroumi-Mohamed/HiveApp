package dev.karroumi.permissionizer;

import java.util.Collection;

/**
 * Strategy for evaluating whether a permission should be granted.
 * Policies act as 'sieves' in the PermissionGuard.
 */
@FunctionalInterface
public interface PermissionPolicy {

    /**
     * Decision made by a policy.
     */
    enum Decision {
        /** Explicitly grant access. Stops further evaluation. */
        GRANTED,
        /** Explicitly deny access. Stops further evaluation. */
        DENIED,
        /** Do not make a decision. Let the next policy in the chain decide. */
        ABSTAIN
    }

    /**
     * Evaluates the requested permission against the current security context.
     *
     * @param requested the permission being checked
     * @param context   optional context object (e.g. RequestContext, CompanyID)
     * @return the decision made by this policy
     */
    Decision evaluate(Permission requested, Object context);

    /**
     * Helper to create a policy from a simple list of authority strings.
     * Implements the standard prefix matching logic.
     */
    static PermissionPolicy fromProvider(PermissionsProvider provider) {
        return (requested, context) -> {
            Collection<String> authorities = provider.getPermissions();
            if (authorities == null) return Decision.ABSTAIN;
            
            return PermissionGuard.matches(requested.path(), authorities) 
                ? Decision.GRANTED 
                : Decision.ABSTAIN;
        };
    }
}
