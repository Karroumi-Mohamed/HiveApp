package dev.karroumi.permissionizer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

/**
 * Runtime permission evaluator with fluent configuration and pluggable policies.
 *
 * <h3>Configuration:</h3>
 *
 * <pre>
 * PermissionGuard
 *         .addPolicy(new SubscriptionPlanPolicy()) // Sieve 1
 *         .addPolicy(new B2bCollaborationPolicy()) // Sieve 2
 *         .addPolicy(PermissionPolicy.fromProvider(myProvider)) // Sieve 3
 *         .withAutoGuard()
 *         .initialize();
 * </pre>
 */
public final class PermissionGuard {

    private static final Logger LOG = Logger.getLogger(PermissionGuard.class.getName());

    private static final List<PermissionPolicy> policies = new ArrayList<>();
    private static volatile boolean autoGuardActive = false;
    private static volatile boolean springInterceptorActive = false;
    private static volatile boolean dryRun = false;
    private static volatile boolean exactMatching = false;
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
     * Starts configuring the guard.
     */
    public static Builder builder() {
        if (initialized.get()) {
            throw new IllegalStateException("PermissionGuard already initialized.");
        }
        return new Builder();
    }

    /**
     * Legacy method for backward compatibility in tests.
     */
    public static Builder permissionProvider(PermissionsProvider provider) {
        return builder().addPolicy(PermissionPolicy.fromProvider(provider));
    }

    /**
     * Shortcut to add a policy and get a builder.
     */
    public static Builder addPolicy(PermissionPolicy policy) {
        return builder().addPolicy(policy);
    }

    public static final class Builder {
        private final List<PermissionPolicy> localPolicies = new ArrayList<>();
        private boolean autoGuard = false;
        private boolean dryRunFlag = false;
        private boolean exactMatchFlag = false;
        private BiConsumer<Permission, Collection<String>> listener;
        private boolean skipVerify = false;

        private Builder() {
        }

        public Builder addPolicy(PermissionPolicy policy) {
            this.localPolicies.add(policy);
            return this;
        }

        public Builder withAutoGuard() {
            this.autoGuard = true;
            return this;
        }

        public Builder dryRun(boolean enabled) {
            this.dryRunFlag = enabled;
            return this;
        }

        public Builder withExactMatching() {
            this.exactMatchFlag = true;
            return this;
        }

        public Builder onDenied(BiConsumer<Permission, Collection<String>> listener) {
            this.listener = listener;
            return this;
        }

        public Builder skipVerification() {
            this.skipVerify = true;
            return this;
        }

        public void initialize() {
            if (!initialized.compareAndSet(false, true)) {
                throw new IllegalStateException("PermissionGuard already initialized.");
            }

            PermissionGuard.policies.addAll(localPolicies);
            PermissionGuard.dryRun = dryRunFlag;
            PermissionGuard.exactMatching = exactMatchFlag;
            PermissionGuard.denialListener = listener;

            if (autoGuard) {
                activateAutoGuard();
                PermissionGuard.autoGuardActive = true;
            }

            if (!skipVerify) {
                verifyGuardAlignment();
            }

            LOG.info("PermissionGuard initialized with " + policies.size() + " policies");
        }
    }

    // ──────────────────────────────────────────────
    // Evaluation Logic
    // ──────────────────────────────────────────────

    public static void check(Permission permission) {
        check(permission, null);
    }

    public static void check(Permission permission, Object context) {
        // Test scope takes absolute priority
        Collection<String> test = testPermissions.get();
        if (test != null) {
            if (!matches(permission.path(), test)) {
                handleDenial(permission, test);
            }
            return;
        }

        if (!evaluatePolicies(permission, context)) {
            handleDenial(permission, List.of("POLICY_RESTRICTION"));
        }
    }

    public static boolean has(Permission permission) {
        return has(permission, null);
    }

    public static boolean has(Permission permission, Object context) {
        Collection<String> test = testPermissions.get();
        if (test != null) {
            return matches(permission.path(), test);
        }
        return evaluatePolicies(permission, context);
    }

    private static boolean evaluatePolicies(Permission permission, Object context) {
        for (PermissionPolicy policy : policies) {
            PermissionPolicy.Decision decision = policy.evaluate(permission, context);
            if (decision == PermissionPolicy.Decision.GRANTED) return true;
            if (decision == PermissionPolicy.Decision.DENIED) return false;
        }
        return false;
    }

    /**
     * Internal matching logic.
     */
    public static boolean matches(String required, Collection<String> authorities) {
        for (String granted : authorities) {
            if (required.equals(granted)) return true;
            if (!exactMatching && required.startsWith(granted + ".")) return true;
        }
        return false;
    }

    // ──────────────────────────────────────────────
    // Legacy support for multi-check (delegates to single check)
    // ──────────────────────────────────────────────

    public static void checkAll(Permission... permissions) {
        for (Permission p : permissions) check(p);
    }

    public static void checkAny(Permission... permissions) {
        for (Permission p : permissions) {
            if (has(p)) return;
        }
        if (permissions.length > 0) handleDenial(permissions[0], List.of("ANY_MATCH_FAILED"));
    }

    // ──────────────────────────────────────────────
    // Testing support
    // ──────────────────────────────────────────────

    /**
     * Creates a scope where the current thread has the given permissions.
     * The previous permissions are restored when the scope is closed.
     * Use in tests to simulate permission contexts.
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
    // Internal & Helpers
    // ──────────────────────────────────────────────

    private static void handleDenial(Permission permission, Collection<String> authorities) {
        if (denialListener != null) {
            denialListener.accept(permission, authorities);
        }
        if (dryRun) {
            LOG.warning("[DRY-RUN] Denied: " + permission.path());
            return;
        }
        throw new PermissionDeniedException(permission.path());
    }

    public static void reset() {
        policies.clear();
        autoGuardActive = false;
        springInterceptorActive = false;
        dryRun = false;
        exactMatching = false;
        denialListener = null;
        initialized.set(false);
        testPermissions.remove();
    }

    private static void activateAutoGuard() {
        try {
            Class.forName("dev.karroumi.permissionizer.agent.AutoGuardActivator")
                    .getDeclaredMethod("activate").invoke(null);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to activate auto-guard", e);
        }
    }

    public static void registerSpringInterceptor() {
        springInterceptorActive = true;
    }

    private static void verifyGuardAlignment() {
        try {
            Class<?> verif = Class.forName("dev.karroumi.permissionizer.generated.PermissionizerVerification");
            boolean guardInCode = (boolean) verif.getField("GUARD_ENABLED").get(null);
            if (guardInCode && !autoGuardActive && !springInterceptorActive) {
                throw new IllegalStateException("SECURITY MISCONFIGURATION: guard=ON found but no agent/interceptor active.");
            }
        } catch (ClassNotFoundException ignored) {
        } catch (Exception e) {
            LOG.warning("Guard verification failed: " + e.getMessage());
        }
    }
}
