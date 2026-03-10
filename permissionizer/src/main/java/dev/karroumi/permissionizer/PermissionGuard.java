package dev.karroumi.permissionizer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

/**
 * Runtime permission evaluator with fluent configuration, prefix matching,
 * type-safe checks, and optional automatic enforcement via bytecode
 * instrumentation.
 *
 * <h3>Configuration:</h3>
 * 
 * <pre>
 * // Manual mode
 * PermissionGuard
 *         .permissionProvider(myProvider)
 *         .initialize();
 *
 * // Auto-guard mode (framework-agnostic)
 * PermissionGuard
 *         .permissionProvider(myProvider)
 *         .withAutoGuard()
 *         .initialize();
 *
 * // With all options
 * PermissionGuard
 *         .permissionProvider(myProvider)
 *         .withAutoGuard()
 *         .dryRun(true)
 *         .onDenied((perm, auths) -&gt; logger.warn("Denied: {}", perm.path()))
 *         .initialize();
 * </pre>
 *
 * <h3>Checking permissions:</h3>
 * 
 * <pre>
 * PermissionGuard.check(Operations.Create.permission());
 * PermissionGuard.checkAll(Operations.all());
 * PermissionGuard.checkAny(Operations.except(Operations.Delete.permission()));
 * </pre>
 *
 * <h3>Testing:</h3>
 * 
 * <pre>
 * try (var scope = PermissionGuard.withPermissions(
 *         Operations.Create.permission(),
 *         Operations.Read.permission())) {
 *     // guarded code passes with these permissions
 * }
 * </pre>
 */
public final class PermissionGuard {

    private static final Logger LOG = Logger.getLogger(PermissionGuard.class.getName());

    private static volatile PermissionsProvider provider;
    private static volatile boolean autoGuardActive = false;
    private static volatile boolean springInterceptorActive = false;
    private static volatile boolean dryRun = false;
    private static volatile BiConsumer<Permission, Collection<String>> denialListener;
    private static final AtomicBoolean initialized = new AtomicBoolean(false);

    // Thread-local override for testing
    private static final ThreadLocal<Collection<String>> testPermissions = new ThreadLocal<>();

    private PermissionGuard() {
    }

    // ──────────────────────────────────────────────
    // Fluent builder
    // ──────────────────────────────────────────────

    /**
     * Starts configuring the guard. Must be called once at application startup.
     *
     * @param provider the provider that supplies current user permissions
     * @return a builder for further configuration
     * @throws IllegalStateException    if already initialized
     * @throws IllegalArgumentException if provider is null
     */
    public static Builder permissionProvider(PermissionsProvider provider) {
        if (initialized.get()) {
            throw new IllegalStateException(
                    "PermissionGuard already initialized. "
                            + "Configuration can only happen once at startup.");
        }
        if (provider == null) {
            throw new IllegalArgumentException("PermissionsProvider must not be null");
        }
        return new Builder(provider);
    }

    /**
     * Fluent builder for guard configuration.
     */
    public static final class Builder {

        private final PermissionsProvider provider;
        private boolean autoGuard = false;
        private boolean dryRunFlag = false;
        private BiConsumer<Permission, Collection<String>> listener;

        private Builder(PermissionsProvider provider) {
            this.provider = provider;
        }

        /**
         * Enables framework-agnostic automatic permission enforcement
         * via Byte Buddy runtime instrumentation. Methods annotated with
         * {@code @PermissionNode(guard = ON)} or under a guarded ancestor
         * will be checked automatically.
         *
         * @return this builder
         */
        public Builder withAutoGuard() {
            this.autoGuard = true;
            return this;
        }

        /**
         * Enables dry-run mode. Permission checks are evaluated and logged
         * but never throw. Useful for gradual rollout on existing codebases.
         *
         * @param enabled true to enable dry-run
         * @return this builder
         */
        public Builder dryRun(boolean enabled) {
            this.dryRunFlag = enabled;
            return this;
        }

        /**
         * Registers a listener called on every permission denial.
         * Invoked before the exception is thrown (or before the log in dry-run mode).
         * Use for audit logging, metrics, or alerting.
         *
         * @param listener receives the denied permission and the user's authorities
         * @return this builder
         */
        public Builder onDenied(BiConsumer<Permission, Collection<String>> listener) {
            this.listener = listener;
            return this;
        }

        private boolean skipVerify = false;

        /**
         * Skips the startup verification check.
         * Only for use in tests where full enforcement is not needed.
         */
        public Builder skipVerification() {
            this.skipVerify = true;
            return this;
        }

        /**
         * Finalizes configuration. After this call, the guard is active
         * and no further configuration is allowed.
         *
         * <p>
         * Performs startup verification: if {@code guard = ON} exists
         * in any {@code @PermissionNode} annotation but no enforcement
         * mechanism is active, the application crashes immediately.
         * </p>
         *
         * @throws IllegalStateException if already initialized or if
         *                               guard misconfiguration is detected
         */
        public void initialize() {
            if (!initialized.compareAndSet(false, true)) {
                throw new IllegalStateException(
                        "PermissionGuard already initialized.");
            }

            PermissionGuard.provider = provider;
            PermissionGuard.dryRun = dryRunFlag;
            PermissionGuard.denialListener = listener;

            if (autoGuard) {
                activateAutoGuard();
                PermissionGuard.autoGuardActive = true;
            }

            if (!skipVerify) {
                verifyGuardAlignment();
            }

            LOG.info("PermissionGuard initialized"
                    + (autoGuard ? " with auto-guard" : " in manual mode")
                    + (dryRunFlag ? " [DRY-RUN]" : ""));
        }
    }

    // ──────────────────────────────────────────────
    // Single permission checks
    // ──────────────────────────────────────────────

    /**
     * Checks if the current user has the required permission.
     * Uses prefix matching — holding "a.b" grants access to "a.b.c".
     *
     * @param permission the permission to check
     * @throws PermissionDeniedException if denied (unless dry-run)
     * @throws IllegalStateException     if not initialized
     */
    public static void check(Permission permission) {
        Collection<String> authorities = resolveAuthorities();
        if (!matches(permission.path(), authorities)) {
            handleDenial(permission, authorities);
        }
    }

    /**
     * Returns true if the current user has the required permission.
     *
     * @param permission the permission to check
     * @return true if granted
     */
    public static boolean has(Permission permission) {
        Collection<String> authorities = safeResolveAuthorities();
        if (authorities == null)
            return false;
        return matches(permission.path(), authorities);
    }

    // ──────────────────────────────────────────────
    // Multi-permission checks
    // ──────────────────────────────────────────────

    /**
     * Checks that the current user has ALL of the given permissions.
     *
     * @param permissions the permissions to check
     * @throws PermissionDeniedException on the first missing permission
     */
    public static void checkAll(Permission... permissions) {
        Collection<String> authorities = resolveAuthorities();
        for (Permission permission : permissions) {
            if (!matches(permission.path(), authorities)) {
                handleDenial(permission, authorities);
            }
        }
    }

    /**
     * Checks that the current user has at least ONE of the given permissions.
     *
     * @param permissions the permissions to check
     * @throws PermissionDeniedException if none are granted
     */
    public static void checkAny(Permission... permissions) {
        Collection<String> authorities = resolveAuthorities();
        for (Permission permission : permissions) {
            if (matches(permission.path(), authorities)) {
                return;
            }
        }
        // None matched — deny with the first permission as the reported one
        if (permissions.length > 0) {
            handleDenial(permissions[0], authorities);
        }
    }

    /**
     * Returns true if the current user has ALL of the given permissions.
     *
     * @param permissions the permissions to check
     * @return true if all are granted
     */
    public static boolean hasAll(Permission... permissions) {
        Collection<String> authorities = safeResolveAuthorities();
        if (authorities == null)
            return false;
        for (Permission permission : permissions) {
            if (!matches(permission.path(), authorities)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true if the current user has at least ONE of the given permissions.
     *
     * @param permissions the permissions to check
     * @return true if any is granted
     */
    public static boolean hasAny(Permission... permissions) {
        Collection<String> authorities = safeResolveAuthorities();
        if (authorities == null)
            return false;
        for (Permission permission : permissions) {
            if (matches(permission.path(), authorities)) {
                return true;
            }
        }
        return false;
    }

    // ──────────────────────────────────────────────
    // Explicit authority checks (no provider)
    // ──────────────────────────────────────────────

    /**
     * Checks a permission against explicit authorities.
     * No provider needed. Useful for testing or non-web contexts.
     *
     * @param permission  the permission to check
     * @param authorities the granted authority strings
     * @throws PermissionDeniedException if denied
     */
    public static void check(Permission permission, Collection<String> authorities) {
        if (authorities == null || !matches(permission.path(), authorities)) {
            handleDenial(permission, authorities != null ? authorities : List.of());
        }
    }

    /**
     * Checks a permission against explicit authorities. Boolean variant.
     *
     * @param permission  the permission to check
     * @param authorities the granted authority strings
     * @return true if granted
     */
    public static boolean has(Permission permission, Collection<String> authorities) {
        if (authorities == null)
            return false;
        return matches(permission.path(), authorities);
    }

    // ──────────────────────────────────────────────
    // Permission listing
    // ──────────────────────────────────────────────

    /**
     * Returns all permissions the current user holds.
     * Reads from the configured provider.
     *
     * @return list of permissions, empty if not initialized or no user
     */
    public static List<Permission> currentPermissions() {
        Collection<String> authorities = safeResolveAuthorities();
        if (authorities == null)
            return List.of();
        return authorities.stream()
                .map(Permission::new)
                .toList();
    }

    /**
     * Returns all of the current user's permissions that fall under
     * the given ancestor permission (including the ancestor itself).
     *
     * @param ancestor the ancestor permission to filter by
     * @return matching permissions
     */
    public static List<Permission> permissionsUnder(Permission ancestor) {
        Collection<String> authorities = safeResolveAuthorities();
        if (authorities == null)
            return List.of();
        String prefix = ancestor.path();
        return authorities.stream()
                .filter(a -> a.equals(prefix) || a.startsWith(prefix + "."))
                .map(Permission::new)
                .toList();
    }

    // ──────────────────────────────────────────────
    // Testing support
    // ──────────────────────────────────────────────

    /**
     * Creates a scope where the current thread has the given permissions.
     * The previous permissions are restored when the scope is closed.
     * Use in tests to simulate permission contexts.
     *
     * <pre>
     * try (var scope = PermissionGuard.withPermissions(
     *         Operations.Create.permission(),
     *         Operations.Read.permission())) {
     *     accountService.createAccount(); // passes
     * }
     * </pre>
     *
     * @param permissions the permissions to grant in this scope
     * @return an AutoCloseable scope
     */
    public static PermissionScope withPermissions(Permission... permissions) {
        List<String> paths = new ArrayList<>(permissions.length);
        for (Permission p : permissions) {
            paths.add(p.path());
        }
        Collection<String> previous = testPermissions.get();
        testPermissions.set(paths);
        return () -> {
            if (previous == null) {
                testPermissions.remove();
            } else {
                testPermissions.set(previous);
            }
        };
    }

    /**
     * AutoCloseable scope returned by {@link #withPermissions}.
     */
    @FunctionalInterface
    public interface PermissionScope extends AutoCloseable {
        @Override
        void close();
    }

    // ──────────────────────────────────────────────
    // Spring interceptor registration
    // ──────────────────────────────────────────────

    /**
     * Called by the Spring auto-configuration to signal that the
     * Spring AOP interceptor is active. Not for public use.
     */
    public static void registerSpringInterceptor() {
        springInterceptorActive = true;
    }

    // ──────────────────────────────────────────────
    // State queries
    // ──────────────────────────────────────────────

    /**
     * Returns true if the guard has been initialized.
     */
    public static boolean isInitialized() {
        return initialized.get();
    }

    /**
     * Returns true if auto-guard is active.
     */
    public static boolean isAutoGuardActive() {
        return autoGuardActive;
    }

    /**
     * Returns true if dry-run mode is active.
     */
    public static boolean isDryRun() {
        return dryRun;
    }

    /**
     * Resets the guard to uninitialized state. For testing only.
     */
    public static void reset() {
        provider = null;
        autoGuardActive = false;
        springInterceptorActive = false;
        dryRun = false;
        denialListener = null;
        initialized.set(false);
        testPermissions.remove();
    }

    // ──────────────────────────────────────────────
    // Core matching
    // ──────────────────────────────────────────────

    /**
     * Tests whether any granted authority covers the required permission
     * via prefix matching with dot-boundary safety.
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

    // ──────────────────────────────────────────────
    // Authority resolution
    // ──────────────────────────────────────────────

    /**
     * Resolves authorities from test scope first, then provider.
     * Throws if not initialized.
     */
    private static Collection<String> resolveAuthorities() {
        // Test scope takes priority
        Collection<String> test = testPermissions.get();
        if (test != null) {
            return test;
        }

        PermissionsProvider p = provider;
        if (p == null) {
            throw new IllegalStateException(
                    "PermissionGuard not initialized. "
                            + "Call PermissionGuard.permissionProvider(p).initialize() at startup.");
        }

        Collection<String> permissions = p.getPermissions();
        if (permissions == null) {
            return List.of();
        }
        return permissions;
    }

    /**
     * Safe version that returns null instead of throwing.
     * Used by boolean methods and listing methods.
     */
    private static Collection<String> safeResolveAuthorities() {
        Collection<String> test = testPermissions.get();
        if (test != null) {
            return test;
        }

        PermissionsProvider p = provider;
        if (p == null)
            return null;

        Collection<String> permissions = p.getPermissions();
        return permissions != null ? permissions : List.of();
    }

    // ──────────────────────────────────────────────
    // Denial handling
    // ──────────────────────────────────────────────

    /**
     * Handles a permission denial — calls listener, logs in dry-run,
     * or throws PermissionDeniedException.
     */
    private static void handleDenial(Permission permission, Collection<String> authorities) {
        // Call listener if registered
        BiConsumer<Permission, Collection<String>> listener = denialListener;
        if (listener != null) {
            try {
                listener.accept(permission, authorities);
            } catch (Exception e) {
                LOG.warning("Denial listener threw: " + e.getMessage());
            }
        }

        if (dryRun) {
            LOG.warning("[DRY-RUN] Permission denied: " + permission.path()
                    + " | authorities: " + authorities);
            return;
        }

        throw new PermissionDeniedException(permission.path());
    }

    // ──────────────────────────────────────────────
    // Auto-guard activation
    // ──────────────────────────────────────────────

    /**
     * Activates Byte Buddy runtime instrumentation for automatic
     * permission checking on guarded methods.
     */
    private static void activateAutoGuard() {
        try {
            Class<?> agentClass = Class.forName("net.bytebuddy.agent.ByteBuddyAgent");
            Class<?> builderClass = Class.forName("net.bytebuddy.agent.builder.AgentBuilder");

            // Delegate to the agent activator to keep Byte Buddy imports isolated
            Class<?> activator = Class.forName(
                    "dev.karroumi.permissionizer.agent.AutoGuardActivator");
            activator.getDeclaredMethod("activate").invoke(null);

            LOG.info("Auto-guard activated via Byte Buddy");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                    "withAutoGuard() requires Byte Buddy on the classpath. "
                            + "Add byte-buddy and byte-buddy-agent dependencies.");
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to activate auto-guard: " + e.getMessage(), e);
        }
    }

    // ──────────────────────────────────────────────
    // Startup verification
    // ──────────────────────────────────────────────

    /**
     * Verifies that if guard=ON exists in code, an enforcement mechanism
     * is active. Crashes immediately if misconfigured.
     */
    private static void verifyGuardAlignment() {
        boolean guardInCode = detectGuardInCode();

        if (!guardInCode) {
            return;
        }

        if (!autoGuardActive && !springInterceptorActive) {
            throw new IllegalStateException(
                    "SECURITY MISCONFIGURATION: @PermissionNode(guard=ON) found in code "
                            + "but no enforcement mechanism is active.\n"
                            + "Methods relying on auto-guard are NOT protected.\n\n"
                            + "Fix: add .withAutoGuard() to your initialization:\n"
                            + "  PermissionGuard\n"
                            + "      .permissionProvider(provider)\n"
                            + "      .withAutoGuard()\n"
                            + "      .initialize();\n\n"
                            + "Or if using Spring Boot, ensure spring-aop is on the classpath.\n"
                            + "If you only use manual PermissionGuard.check() calls, "
                            + "remove guard=ON from your annotations.");
        }
    }

    /**
     * Checks if the processor generated a verification class indicating
     * that guard=ON exists somewhere in the codebase.
     */
    private static boolean detectGuardInCode() {
        try {
            Class<?> verification = Class.forName(
                    "dev.karroumi.permissionizer.generated.PermissionizerVerification");
            return (boolean) verification.getField("GUARD_ENABLED").get(null);
        } catch (ClassNotFoundException e) {
            return false;
        } catch (Exception e) {
            LOG.warning("Failed to read verification class: " + e.getMessage());
            return false;
        }
    }
}
