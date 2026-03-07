package dev.karroumi.permissionizer;

import java.util.Collection;

/**
 * Supplies the current user's granted permission strings to the library.
 *
 * <p>The host application implements this interface to bridge its authentication
 * framework with the permission library. The library never knows where the
 * permissions come from — Spring Security, Jakarta EE, a custom framework,
 * or a plain thread-local.</p>
 *
 * <p>The implementation must return the permission strings for the user
 * associated with the current execution context (typically the current thread).</p>
 *
 * <h3>Spring Boot example:</h3>
 * <pre>
 * public class SpringPermissionsProvider implements PermissionsProvider {
 *     public Collection&lt;String&gt; getPermissions() {
 *         var auth = SecurityContextHolder.getContext().getAuthentication();
 *         return auth.getAuthorities().stream()
 *             .map(GrantedAuthority::getAuthority)
 *             .toList();
 *     }
 * }
 * </pre>
 *
 * <h3>Plain Java example:</h3>
 * <pre>
 * public class ThreadLocalPermissionsProvider implements PermissionsProvider {
 *     private static final ThreadLocal&lt;List&lt;String&gt;&gt; CURRENT = new ThreadLocal&lt;&gt;();
 *
 *     public static void set(List&lt;String&gt; permissions) { CURRENT.set(permissions); }
 *     public Collection&lt;String&gt; getPermissions() { return CURRENT.get(); }
 * }
 * </pre>
 *
 * <p>Register with {@link PermissionGuard#configure(PermissionsProvider)} once at startup.</p>
 */
@FunctionalInterface
public interface PermissionsProvider {

    /**
     * Returns the permission strings granted to the current user.
     * Called by {@link PermissionGuard} on every permission check.
     *
     * @return the current user's granted permission paths, never null
     */
    Collection<String> getPermissions();
}
