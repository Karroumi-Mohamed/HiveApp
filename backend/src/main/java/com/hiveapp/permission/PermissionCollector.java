package com.hiveapp.permission;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Reflects over generated permission tree classes to collect all permission paths.
 *
 * <p>Reads {@code META-INF/permission-roots.idx} from the classpath to discover
 * root permission classes, then walks their nested classes recursively to extract
 * all permission paths.</p>
 *
 * <p>Zero external dependencies. No side effects. Never touches a database.</p>
 *
 * <h3>Usage:</h3>
 * <pre>
 * // Automatic — reads index file, finds everything
 * List&lt;PermissionNode&gt; nodes = PermissionCollector.collect();
 *
 * // Explicit — pass root classes directly
 * List&lt;PermissionNode&gt; nodes = PermissionCollector.collect(PlatformPermissions.class);
 * </pre>
 */
public final class PermissionCollector {

    private static final String INDEX_FILE = "META-INF/permission-roots.idx";

    private PermissionCollector() {}

    /**
     * Collects all permission nodes by reading the generated index file.
     * No arguments needed — the processor maintains the index automatically.
     *
     * @return an unmodifiable list of all permission nodes, sorted by path
     */
    public static List<PermissionNode> collect() {
        List<Class<?>> roots = loadRootsFromIndex();
        return collectFromClasses(roots);
    }

    /**
     * Collects permission nodes from specific root classes.
     * Use this if the index file is unavailable or you want explicit control.
     *
     * @param rootClasses the generated root permission classes
     * @return an unmodifiable list of all permission nodes, sorted by path
     */
    public static List<PermissionNode> collect(Class<?>... rootClasses) {
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
                        // Skip — class was removed but index not yet regenerated
                    }
                }
            }
        } catch (Exception e) {
            // Index file unreadable — return empty
        }

        return roots;
    }

    // ──────────────────────────────────────────────
    // Tree walking
    // ──────────────────────────────────────────────

    private static List<PermissionNode> collectFromClasses(List<Class<?>> rootClasses) {
        Map<String, PermissionNode> nodes = new LinkedHashMap<>();

        for (Class<?> root : rootClasses) {
            walkClass(root, nodes);
        }

        return nodes.values().stream()
                .sorted(Comparator.comparing(PermissionNode::path))
                .toList();
    }

    /**
     * Reads the $ field from a class, creates a PermissionNode,
     * then recurses into all nested classes.
     */
    private static void walkClass(Class<?> clazz, Map<String, PermissionNode> nodes) {
        String path = readPathField(clazz);
        if (path == null) {
            return;
        }

        String parentPath = deriveParentPath(path);
        nodes.put(path, new PermissionNode(path, "", parentPath));

        // Recurse into nested classes
        for (Class<?> nested : clazz.getDeclaredClasses()) {
            walkClass(nested, nodes);
        }
    }

    /**
     * Reads the public static final String $ field from a class.
     * Returns null if the field doesn't exist or isn't accessible.
     */
    private static String readPathField(Class<?> clazz) {
        try {
            Field field = clazz.getDeclaredField("$");
            int modifiers = field.getModifiers();

            if (Modifier.isPublic(modifiers)
                    && Modifier.isStatic(modifiers)
                    && Modifier.isFinal(modifiers)
                    && field.getType() == String.class) {
                return (String) field.get(null);
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            // Not a permission class — skip
        }
        return null;
    }

    private static String deriveParentPath(String path) {
        int lastDot = path.lastIndexOf('.');
        return lastDot >= 0 ? path.substring(0, lastDot) : null;
    }
}