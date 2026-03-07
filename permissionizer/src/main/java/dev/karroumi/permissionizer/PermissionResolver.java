package dev.karroumi.permissionizer;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves permission paths and guard status for methods at runtime.
 *
 * <p>Used by host application interceptors (Spring AOP, CDI, etc.) to
 * automatically enforce permissions without manual {@link PermissionGuard#check}
 * calls in service methods.</p>
 *
 * <p>Results are cached — each method is resolved once on first access.
 * Thread-safe via {@link ConcurrentHashMap}.</p>
 *
 * <h3>Spring AOP example in the host application:</h3>
 * <pre>
 * {@literal @}Aspect
 * {@literal @}Component
 * public class PermissionInterceptor {
 *
 *     {@literal @}Around(
 *         "{@literal @}annotation(dev.karroumi.permissionizer.PermissionNode) || " +
 *         "{@literal @}within(dev.karroumi.permissionizer.PermissionNode)")
 *     public Object enforce(ProceedingJoinPoint joinPoint) throws Throwable {
 *         Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
 *         PermissionResolver.Result result = PermissionResolver.resolve(method);
 *         if (result.shouldCheck()) {
 *             PermissionGuard.check(result.path());
 *         }
 *         return joinPoint.proceed();
 *     }
 * }
 * </pre>
 *
 * <h3>Guard resolution:</h3>
 * <ol>
 *   <li>Method has {@code guard = OFF} → no check</li>
 *   <li>Method has {@code guard = ON} → check method's path</li>
 *   <li>Method has {@code guard = INHERIT} → walk up to class, then packages</li>
 *   <li>Method has no annotation, class has annotation with guard active → check class's path</li>
 *   <li>Nothing found → no check</li>
 * </ol>
 */
public final class PermissionResolver {

    private static final Map<Method, Result> cache = new ConcurrentHashMap<>();

    private PermissionResolver() {}

    /**
     * The result of resolving a method's permission path and guard status.
     *
     * @param path the resolved dot-path, or null if unresolvable
     * @param shouldCheck true if the guard should be enforced for this method
     */
    public record Result(String path, boolean shouldCheck) {

        /** Result indicating no check should be performed. */
        public static final Result SKIP = new Result(null, false);
    }

    /**
     * Resolves the permission path and guard status for a method.
     * Results are cached — safe to call on every request.
     *
     * @param method the method being invoked
     * @return the resolution result with path and guard status
     */
    public static Result resolve(Method method) {
        return cache.computeIfAbsent(method, PermissionResolver::doResolve);
    }

    /**
     * Clears the resolution cache. Useful for testing.
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
     * Resolve its path and determine guard status.
     */
    private static Result resolveAnnotatedMethod(Method method,
                                                  PermissionNode annotation) {
        // Explicit OFF — never check
        if (annotation.guard() == PermissionNode.Guard.OFF) {
            return Result.SKIP;
        }

        String path = resolveMethodPath(method, annotation);
        if (path == null) {
            return Result.SKIP;
        }

        // Explicit ON — always check
        if (annotation.guard() == PermissionNode.Guard.ON) {
            return new Result(path, true);
        }

        // INHERIT — check if any ancestor has guard ON
        boolean guardActive = isGuardActiveForClass(method.getDeclaringClass());
        return new Result(path, guardActive);
    }

    /**
     * Method has no @PermissionNode.
     * Check if enclosing class has annotation with active guard.
     * If so, check at the class's path level.
     */
    private static Result resolveUnannotatedMethod(Method method) {
        // Skip non-public methods
        if (!Modifier.isPublic(method.getModifiers())) {
            return Result.SKIP;
        }

        // Skip Object methods
        if (method.getDeclaringClass() == Object.class) {
            return Result.SKIP;
        }
        try {
            Object.class.getMethod(method.getName(), method.getParameterTypes());
            return Result.SKIP; // method exists on Object — skip it
        } catch (NoSuchMethodException e) {
            // not an Object method — continue
        }

        Class<?> clazz = method.getDeclaringClass();
        PermissionNode classAnnotation = clazz.getAnnotation(PermissionNode.class);

        if (classAnnotation == null) {
            return Result.SKIP;
        }

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
        if (classAnnotation.autoDiscover()) {
            return new Result(classPath + "." + method.getName(), true);
        }

        // No autoDiscover — check at class level
        return new Result(classPath, true);
    }

    // ──────────────────────────────────────────────
    // Path resolution
    // ──────────────────────────────────────────────

    /**
     * Resolves the full dot-path for an annotated method.
     */
    private static String resolveMethodPath(Method method, PermissionNode annotation) {
        String key = annotation.key();

        // Check explicit parent
        Class<?> parentClass = annotation.parent();
        if (parentClass != Void.class) {
            String parentPath = resolveClassPath(parentClass);
            return parentPath != null ? parentPath + "." + key : null;
        }

        // Try enclosing class
        Class<?> enclosingClass = method.getDeclaringClass();
        PermissionNode classAnnotation = enclosingClass.getAnnotation(PermissionNode.class);
        if (classAnnotation != null) {
            String parentPath = resolveClassPath(enclosingClass);
            return parentPath != null ? parentPath + "." + key : null;
        }

        // Walk up packages
        String parentPath = resolvePackagePath(enclosingClass.getPackage());
        return parentPath != null ? parentPath + "." + key : null;
    }

    /**
     * Resolves the full dot-path for an annotated class.
     */
    public static String resolveClassPath(Class<?> clazz) {
        PermissionNode annotation = clazz.getAnnotation(PermissionNode.class);
        if (annotation == null) {
            return null;
        }

        String key = annotation.key();

        // Check explicit parent
        Class<?> parentClass = annotation.parent();
        if (parentClass != Void.class) {
            String parentPath = resolveClassPath(parentClass);
            return parentPath != null ? parentPath + "." + key : null;
        }

        // Walk up packages
        String parentPath = resolvePackagePath(clazz.getPackage());
        return parentPath != null ? parentPath + "." + key : null;
    }

    /**
     * Resolves the full dot-path for a package by walking up the hierarchy.
     * Returns the path of the nearest annotated ancestor package, or the
     * package's own path if it's annotated.
     */
    private static String resolvePackagePath(Package pkg) {
        if (pkg == null) {
            return null;
        }

        PermissionNode annotation = pkg.getAnnotation(PermissionNode.class);
        if (annotation != null) {
            String key = annotation.key();
            String parentPath = resolveParentPackagePath(pkg);
            return parentPath != null ? parentPath + "." + key : key;
        }

        return resolveParentPackagePath(pkg);
    }

    /**
     * Walks up from a package to find the nearest annotated ancestor.
     */
    private static String resolveParentPackagePath(Package startPackage) {
        String packageName = startPackage.getName();

        while (packageName.contains(".")) {
            int lastDot = packageName.lastIndexOf('.');
            packageName = packageName.substring(0, lastDot);

            Package parentPkg = Package.getPackage(packageName);
            if (parentPkg != null) {
                PermissionNode annotation = parentPkg.getAnnotation(PermissionNode.class);
                if (annotation != null) {
                    String key = annotation.key();
                    String grandParentPath = resolveParentPackagePath(parentPkg);
                    return grandParentPath != null
                            ? grandParentPath + "." + key
                            : key;
                }
            }
        }

        return null;
    }

    // ──────────────────────────────────────────────
    // Guard inheritance
    // ──────────────────────────────────────────────

    /**
     * Determines if guard is active for a class by checking the class
     * annotation and walking up the package hierarchy.
     */
    private static boolean isGuardActiveForClass(Class<?> clazz) {
        PermissionNode annotation = clazz.getAnnotation(PermissionNode.class);
        if (annotation != null) {
            if (annotation.guard() == PermissionNode.Guard.ON) return true;
            if (annotation.guard() == PermissionNode.Guard.OFF) return false;
        }

        // INHERIT or no annotation — walk up packages
        return isGuardActiveInPackages(clazz.getPackage());
    }

    /**
     * Walks up the package hierarchy checking for guard settings.
     * Returns true if any ancestor has guard = ON.
     * Returns false if an ancestor has guard = OFF or no ancestor has a setting.
     */
    private static boolean isGuardActiveInPackages(Package pkg) {
        if (pkg == null) return false;

        PermissionNode annotation = pkg.getAnnotation(PermissionNode.class);
        if (annotation != null) {
            if (annotation.guard() == PermissionNode.Guard.ON) return true;
            if (annotation.guard() == PermissionNode.Guard.OFF) return false;
        }

        // INHERIT — keep walking
        String name = pkg.getName();
        if (name.contains(".")) {
            String parentName = name.substring(0, name.lastIndexOf('.'));
            Package parentPkg = Package.getPackage(parentName);
            if (parentPkg != null) {
                return isGuardActiveInPackages(parentPkg);
            }
        }

        return false;
    }
}