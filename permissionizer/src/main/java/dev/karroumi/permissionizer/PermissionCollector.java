package dev.karroumi.permissionizer;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Collects all permission paths from generated permission tree classes.
 *
 * <p>
 * Reads {@code META-INF/permission-roots.idx} from the classpath to discover
 * root permission classes, walks their nested classes recursively, reads
 * {@code permission()} methods, and loads descriptions from
 * {@code descriptions()}.
 * </p>
 *
 * <p>
 * Zero external dependencies. No side effects. Never touches a database.
 * </p>
 *
 * <h3>Usage:</h3>
 * 
 * <pre>
 * // Automatic — discovers roots from the index file
 * List&lt;CollectedPermission&gt; permissions = PermissionCollector.collect();
 *
 * // Explicit — pass root classes directly
 * List&lt;CollectedPermission&gt; permissions = PermissionCollector.collect(
 *         PlatformPermissions.class);
 * </pre>
 */
public final class PermissionCollector {

    private static final String INDEX_FILE = "META-INF/permission-roots.idx";

    private PermissionCollector() {
    }

    /**
     * Collects all permission nodes by reading the generated index file.
     * The annotation processor maintains the index automatically.
     *
     * @return an unmodifiable list of all permission nodes, sorted by path
     */
    public static List<CollectedPermission> collect() {
        List<Class<?>> roots = loadRootsFromIndex();
        return collectFromClasses(roots);
    }

    /**
     * Collects permission nodes from specific root classes.
     * Use when you want explicit control over which roots to scan.
     *
     * @param rootClasses the generated root permission classes
     * @return an unmodifiable list of all permission nodes, sorted by path
     */
    public static List<CollectedPermission> collect(Class<?>... rootClasses) {
        return collectFromClasses(Arrays.asList(rootClasses));
    }

    // ──────────────────────────────────────────────
    // Index file reading
    // ──────────────────────────────────────────────

    private static List<Class<?>> loadRootsFromIndex() {
        List<Class<?>> roots = new ArrayList<>();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        try (InputStream is = classLoader.getResourceAsStream(INDEX_FILE)) {
            if (is == null) {
                return roots;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    try {
                        roots.add(Class.forName(line, false, classLoader));
                    } catch (ClassNotFoundException e) {
                        // Class was removed but index not yet regenerated
                    }
                }
            }
        } catch (Exception e) {
            // Index file unreadable
        }

        return roots;
    }

    // ──────────────────────────────────────────────
    // Tree walking
    // ──────────────────────────────────────────────

    private static List<CollectedPermission> collectFromClasses(List<Class<?>> rootClasses) {
        Map<String, CollectedPermission> nodes = new LinkedHashMap<>();
        Map<String, String> allDescriptions = new HashMap<>();

        for (Class<?> root : rootClasses) {
            walkClass(root, nodes);
            loadDescriptions(root, allDescriptions);
        }

        // Merge descriptions into nodes
        Map<String, CollectedPermission> enriched = new LinkedHashMap<>();
        for (var entry : nodes.entrySet()) {
            String path = entry.getKey();
            String description = allDescriptions.getOrDefault(path, "");
            enriched.put(path, new CollectedPermission(
                    path, description, entry.getValue().parentPath()));
        }

        return enriched.values().stream()
                .sorted(Comparator.comparing(CollectedPermission::path))
                .toList();
    }

    /**
     * Reads the permission() method from a class, creates a CollectedPermission,
     * then recurses into all declared nested classes.
     */
    private static void walkClass(Class<?> clazz, Map<String, CollectedPermission> nodes) {
        String path = readPermissionPath(clazz);
        if (path == null) {
            return;
        }

        String parentPath = deriveParentPath(path);
        nodes.put(path, new CollectedPermission(path, "", parentPath));

        for (Class<?> nested : clazz.getDeclaredClasses()) {
            walkClass(nested, nodes);
        }
    }

    /**
     * Reads the path from a class by calling its static permission() method.
     * Returns null if the method doesn't exist or fails.
     */
    private static String readPermissionPath(Class<?> clazz) {
        try {
            Method method = clazz.getDeclaredMethod("permission");
            int modifiers = method.getModifiers();

            if (Modifier.isPublic(modifiers)
                    && Modifier.isStatic(modifiers)
                    && method.getReturnType() == Permission.class) {
                Permission permission = (Permission) method.invoke(null);
                return permission != null ? permission.path() : null;
            }
        } catch (NoSuchMethodException e) {
            // Not a permission class
        } catch (Exception e) {
            // Invocation failed
        }
        return null;
    }

    /**
     * Calls the static descriptions() method on a root class to load
     * all path-description mappings.
     */
    @SuppressWarnings("unchecked")
    private static void loadDescriptions(Class<?> rootClass,
            Map<String, String> descriptions) {
        try {
            Method method = rootClass.getDeclaredMethod("descriptions");
            int modifiers = method.getModifiers();

            if (Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers)) {
                Map<String, String> map = (Map<String, String>) method.invoke(null);
                if (map != null) {
                    descriptions.putAll(map);
                }
            }
        } catch (Exception e) {
            // No descriptions method or invocation failed
        }
    }

    /**
     * Derives the parent path by stripping the last dot segment.
     * "erp.hr.payroll" becomes "erp.hr". "erp" becomes null (root).
     */
    private static String deriveParentPath(String path) {
        int lastDot = path.lastIndexOf('.');
        return lastDot >= 0 ? path.substring(0, lastDot) : null;
    }
}
