package dev.karroumi.permissionizer;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Resolves permission paths and guard status for methods at runtime.
 *
 * <p>
 * Used by interceptors (Spring AOP, Byte Buddy agent, CDI, etc.) to
 * determine if a method should be auto-checked and what path to check.
 * Results are cached per method — first resolution pays the reflection cost,
 * all subsequent lookups are a map read.
 * </p>
 *
 * <h3>Resolution rules:</h3>
 * <ol>
 * <li>Method has {@code @PermissionNode(guard = OFF)} → skip</li>
 * <li>Method has {@code @PermissionNode(guard = ON)} → check method's path</li>
 * <li>Method has {@code @PermissionNode(guard = INHERIT)} → check if ancestor
 * has ON</li>
 * <li>Method has no annotation, class has annotation with active guard → check
 * at class path</li>
 * <li>Method has no annotation, class has autoDiscover → check at class path +
 * method name</li>
 * <li>Nothing applies → skip</li>
 * </ol>
 *
 * <h3>Key derivation:</h3>
 * <p>
 * If a method's {@code @PermissionNode} has an empty key, the method name is
 * used.
 * This matches the processor's behavior at compile time.
 * </p>
 */
public final class PermissionResolver {

    private static final Logger LOG = Logger.getLogger(PermissionResolver.class.getName());

    private static final Map<Method, Result> cache = new ConcurrentHashMap<>();

    private PermissionResolver() {
    }

    /**
     * The result of resolving a method's permission and guard status.
     *
     * @param permission  the resolved permission, or null if unresolvable
     * @param shouldCheck true if the guard should be enforced
     */
    public record Result(Permission permission, boolean shouldCheck) {

        /** Result indicating no check should be performed. */
        public static final Result SKIP = new Result(null, false);

        /**
         * Convenience — returns the path string or null.
         */
        public String path() {
            return permission != null ? permission.path() : null;
        }
    }

    /**
     * Resolves the permission and guard status for a method.
     * Results are cached — safe to call on every request.
     *
     * @param method the method being invoked
     * @return the resolution result
     */
    public static Result resolve(Method method) {
        Result r = cache.computeIfAbsent(method, PermissionResolver::doResolve);
        if (LOG.isLoggable(java.util.logging.Level.FINE)) {
            LOG.fine("[Permissionizer] Resolved " + method.getName() + " -> " + r);
        }
        return r;
    }

    /**
     * Clears the resolution cache. For testing only.
     */
    public static void clearCache() {
        cache.clear();
    }

    // ──────────────────────────────────────────────
    // Core resolution
    // ──────────────────────────────────────────────

    private static Result doResolve(Method method) {
        PermissionNode methodAnnotation = method.getAnnotation(PermissionNode.class);

        if (methodAnnotation != null) {
            return resolveAnnotatedMethod(method, methodAnnotation);
        }

        return resolveUnannotatedMethod(method);
    }

    /**
     * Method has its own @PermissionNode.
     * Resolve key (explicit or derived from method name), build path,
     * determine guard status.
     */
    private static Result resolveAnnotatedMethod(Method method,
            PermissionNode annotation) {
        // Explicit OFF — never check
        if (annotation.guard() == PermissionNode.Guard.OFF) {
            return Result.SKIP;
        }

        // Derive key — use annotation key if present, otherwise method name
        String key = annotation.key();
        if (key == null || key.isEmpty()) {
            key = method.getName();
        }

        String path = resolveMethodPath(method, key, annotation);
        if (path == null) {
            LOG.warning("Could not resolve path for method: "
                    + method.getDeclaringClass().getName() + "#" + method.getName());
            return Result.SKIP;
        }

        Permission permission = new Permission(path);

        // Explicit ON — always check
        if (annotation.guard() == PermissionNode.Guard.ON) {
            return new Result(permission, true);
        }

        // INHERIT — check if any ancestor has guard ON
        boolean guardActive = isGuardActiveForClass(method.getDeclaringClass());
        return new Result(permission, guardActive);
    }

    /**
     * Method has no @PermissionNode.
     * Check if enclosing class has annotation with active guard.
     * If autoDiscover, use method name as leaf key.
     */
    private static Result resolveUnannotatedMethod(Method method) {
        // Skip non-public
        if (!Modifier.isPublic(method.getModifiers())) {
            return Result.SKIP;
        }

        // Skip static
        if (Modifier.isStatic(method.getModifiers())) {
            return Result.SKIP;
        }

        // Skip Object methods
        if (isObjectMethod(method)) {
            return Result.SKIP;
        }

        Class<?> clazz = method.getDeclaringClass();
        PermissionNode annotation = clazz.getAnnotation(PermissionNode.class);

        if (annotation != null) {
            // Class has annotation — check if guard is active
            if (!isGuardActiveForClass(clazz)) {
                return Result.SKIP;
            }

            // Guard is active — resolve class path
            String classPath = resolveClassPath(clazz);
            if (classPath == null) {
                return Result.SKIP;
            }

            // If autoDiscover, use method name as leaf key
            if (annotation.autoDiscover()) {
                Permission permission = new Permission(classPath + "." + method.getName());
                return new Result(permission, true);
            }

            // No autoDiscover — check at class level
            Permission permission = new Permission(classPath);
            return new Result(permission, true);
        }

        // No class annotation — check if package has guard ON
        if (isGuardActiveInPackages(clazz.getPackage())) {
            String pkgPath = resolvePackagePath(clazz.getPackage());
            if (pkgPath != null) {
                Permission permission = new Permission(pkgPath + "." + method.getName());
                return new Result(permission, true);
            }
        }

        return Result.SKIP;
    }

    // ──────────────────────────────────────────────
    // Path resolution
    // ──────────────────────────────────────────────

    /**
     * Resolves the full dot-path for an annotated method.
     * Walks: explicit parent → enclosing class → packages.
     */
    private static String resolveMethodPath(Method method, String key,
            PermissionNode annotation) {
        // Check explicit parent
        Class<?> parentClass = annotation.parent();
        if (parentClass != Void.class) {
            String parentPath = resolveClassPath(parentClass);
            return parentPath != null ? parentPath + "." + key : key;
        }

        // Try enclosing class
        Class<?> enclosingClass = method.getDeclaringClass();
        PermissionNode classAnnotation = enclosingClass.getAnnotation(PermissionNode.class);
        if (classAnnotation != null) {
            String parentPath = resolveClassPath(enclosingClass);
            return parentPath != null ? parentPath + "." + key : key;
        }

        // Walk up packages
        String parentPath = resolvePackagePath(enclosingClass.getPackage());
        return parentPath != null ? parentPath + "." + key : key;
    }

    /**
     * Resolves the full dot-path for an annotated class.
     * Key is required on classes — empty key returns null.
     */
    public static String resolveClassPath(Class<?> clazz) {
        PermissionNode annotation = clazz.getAnnotation(PermissionNode.class);
        if (annotation == null) {
            return null;
        }

        String key = annotation.key();
        if (key == null || key.isEmpty()) {
            LOG.warning("@PermissionNode on class " + clazz.getName()
                    + " has no key. Key is required on classes.");
            return null;
        }

        // Check explicit parent
        Class<?> parentClass = annotation.parent();
        if (parentClass != Void.class) {
            String parentPath = resolveClassPath(parentClass);
            return parentPath != null ? parentPath + "." + key : key;
        }

        // Walk up packages
        String parentPath = resolvePackagePath(clazz.getPackage());
        return parentPath != null ? parentPath + "." + key : key;
    }

    /**
     * Resolves the full dot-path for a package.
     * If the package is annotated, builds its path by walking up ancestors.
     * If not annotated, walks up to find the nearest annotated ancestor.
     */
    private static String resolvePackagePath(Package pkg) {
        if (pkg == null) {
            return null;
        }

        PermissionNode annotation = getPackageAnnotation(pkg.getName());
        if (annotation != null) {
            String key = annotation.key();
            if (key == null || key.isEmpty()) {
                return null;
            }
            String parentPath = resolveParentPackagePath(pkg.getName());
            return parentPath != null ? parentPath + "." + key : key;
        }

        return resolveParentPackagePath(pkg.getName());
    }

    /**
     * Walks up from a package name to find the nearest annotated ancestor
     * and resolves its full path recursively.
     */
    private static String resolveParentPackagePath(String packageName) {
        while (packageName.contains(".")) {
            int lastDot = packageName.lastIndexOf('.');
            packageName = packageName.substring(0, lastDot);

            PermissionNode annotation = getPackageAnnotation(packageName);
            if (annotation != null) {
                String key = annotation.key();
                if (key == null || key.isEmpty()) {
                    continue;
                }
                String grandParentPath = resolveParentPackagePath(packageName);
                return grandParentPath != null
                        ? grandParentPath + "." + key
                        : key;
            }
        }

        return null;
    }

    // ──────────────────────────────────────────────
    // Guard inheritance
    // ──────────────────────────────────────────────

    /**
     * Determines if guard is active for a class by checking the class
     * annotation then walking up the package hierarchy.
     */
    private static boolean isGuardActiveForClass(Class<?> clazz) {
        PermissionNode annotation = clazz.getAnnotation(PermissionNode.class);
        if (annotation != null) {
            if (annotation.guard() == PermissionNode.Guard.ON) {
                if (LOG.isLoggable(java.util.logging.Level.FINE)) {
                    LOG.fine("[Permissionizer] Guard ON via class annotation: " + clazz.getName());
                }
                return true;
            }
            if (annotation.guard() == PermissionNode.Guard.OFF) {
                return false;
            }
        }

        boolean active = isGuardActiveInPackages(clazz.getPackage());
        if (LOG.isLoggable(java.util.logging.Level.FINE)) {
            LOG.fine("[Permissionizer] Guard for " + clazz.getName() + " active: " + active);
        }
        return active;
    }

    /**
     * Walks up the package hierarchy checking for guard settings.
     * Returns true if any ancestor has guard = ON before encountering OFF.
     */
    private static boolean isGuardActiveInPackages(Package pkg) {
        if (pkg == null)
            return false;

        String name = pkg.getName();
        while (name != null) {
            PermissionNode annotation = getPackageAnnotation(name);
            if (annotation != null) {
                if (annotation.guard() == PermissionNode.Guard.ON)
                    return true;
                if (annotation.guard() == PermissionNode.Guard.OFF)
                    return false;
            }

            if (!name.contains(".")) {
                break;
            }
            name = name.substring(0, name.lastIndexOf('.'));
        }

        return false;
    }

    private static PermissionNode getPackageAnnotation(String packageName) {
        try {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            Class<?> pkgInfo = Class.forName(packageName + ".package-info", false, loader);
            return pkgInfo.getAnnotation(PermissionNode.class);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    // ──────────────────────────────────────────────
    // Utility
    // ──────────────────────────────────────────────

    /**
     * Checks if a method is inherited from Object and should not
     * be treated as a permission target.
     */
    private static boolean isObjectMethod(Method method) {
        String name = method.getName();
        int paramCount = method.getParameterCount();

        return switch (name) {
            case "toString" -> paramCount == 0;
            case "hashCode" -> paramCount == 0;
            case "equals" -> paramCount == 1;
            case "clone" -> paramCount == 0;
            case "finalize" -> paramCount == 0;
            case "getClass" -> paramCount == 0;
            case "notify" -> paramCount == 0;
            case "notifyAll" -> paramCount == 0;
            case "wait" -> paramCount <= 2;
            default -> false;
        };
    }
}
