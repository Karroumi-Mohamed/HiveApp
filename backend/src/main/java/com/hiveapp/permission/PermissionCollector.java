package com.hiveapp.permission;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Reflects over generated companion classes to collect all permission paths
 * defined in the current build.
 *
 * <p>This class has zero external dependencies. It uses standard Java reflection
 * to scan for classes ending with "Permissions" in the given package tree,
 * reads their {@code public static final String} fields, and returns
 * structured {@link PermissionNode} objects.</p>
 *
 * <p>The host application uses the output for database seeding, admin UI rendering,
 * orphan detection, or any other purpose. This class never touches a database
 * and never performs side effects.</p>
 *
 * <h3>Usage:</h3>
 * <pre>
 * List&lt;PermissionNode&gt; nodes = PermissionCollector.collect("com.erp");
 * </pre>
 */
public final class PermissionCollector {

    private PermissionCollector() {}

    /**
     * Collects all permission nodes from generated companion classes
     * found in the given base package and its sub-packages.
     *
     * <p>Scans the classpath for classes whose names end with "Permissions",
     * reads their {@code public static final String} fields, and reconstructs
     * the tree structure from the dot-paths.</p>
     *
     * @param basePackage the root package to scan (e.g., "com.hiveapp")
     * @return an unmodifiable list of all permission nodes, sorted by path
     */
    public static List<PermissionNode> collect(String basePackage) {
        List<Class<?>> companionClasses = findCompanionClasses(basePackage);
        Map<String, PermissionNode> nodes = new LinkedHashMap<>();

        for (Class<?> clazz : companionClasses) {
            extractNodes(clazz, nodes);
        }

        return nodes.values().stream()
                .sorted(Comparator.comparing(PermissionNode::path))
                .toList();
    }

    /**
     * Collects permission nodes from a specific set of companion classes.
     * Useful when you know exactly which classes to scan.
     *
     * @param companionClasses the generated companion classes to scan
     * @return an unmodifiable list of all permission nodes, sorted by path
     */
    public static List<PermissionNode> collect(Class<?>... companionClasses) {
        Map<String, PermissionNode> nodes = new LinkedHashMap<>();

        for (Class<?> clazz : companionClasses) {
            extractNodes(clazz, nodes);
        }

        return nodes.values().stream()
                .sorted(Comparator.comparing(PermissionNode::path))
                .toList();
    }

    /**
     * Extracts all permission string constants from a single companion class.
     * For each constant, derives the parent path from the dot-path structure
     * and creates a {@link PermissionNode}.
     */
    private static void extractNodes(Class<?> clazz, Map<String, PermissionNode> nodes) {
        for (Field field : clazz.getDeclaredFields()) {
            if (!isPermissionConstant(field)) {
                continue;
            }

            try {
                String path = (String) field.get(null);
                if (path == null || path.isEmpty()) continue;

                // Derive parent path from the dot-path
                String parentPath = deriveParentPath(path);

                // Try to get description from the field's javadoc annotation
                // Since javadoc isn't available at runtime, we extract the description
                // from the generated comment pattern: "description — path"
                String description = extractDescription(field);

                nodes.put(path, new PermissionNode(path, description, parentPath));

                // Ensure all intermediate parent nodes exist
                ensureParentNodes(path, nodes);

            } catch (IllegalAccessException e) {
                // Skip inaccessible fields
            }
        }
    }

    /**
     * Checks if a field is a permission string constant:
     * public static final String.
     */
    private static boolean isPermissionConstant(Field field) {
        int modifiers = field.getModifiers();
        return Modifier.isPublic(modifiers)
                && Modifier.isStatic(modifiers)
                && Modifier.isFinal(modifiers)
                && field.getType() == String.class;
    }

    /**
     * Derives the parent path from a dot-path.
     * "erp.hr.payroll.export" → "erp.hr.payroll"
     * "erp" → null (root node)
     */
    private static String deriveParentPath(String path) {
        int lastDot = path.lastIndexOf('.');
        return lastDot >= 0 ? path.substring(0, lastDot) : null;
    }

    /**
     * Ensures all intermediate parent nodes exist in the map.
     * For "erp.hr.payroll.export", ensures "erp.hr.payroll", "erp.hr", and "erp" exist.
     * This handles cases where a parent node might not have its own companion class
     * but is implied by the path structure.
     */
    private static void ensureParentNodes(String path, Map<String, PermissionNode> nodes) {
        String current = path;
        while (current.contains(".")) {
            String parent = deriveParentPath(current);
            if (parent != null && !nodes.containsKey(parent)) {
                String grandParent = deriveParentPath(parent);
                nodes.put(parent, new PermissionNode(parent, "", grandParent));
            }
            current = parent;
            if (current == null) break;
        }
    }

    /**
     * Attempts to extract a description from the field.
     * The generated companion classes don't carry runtime-accessible descriptions
     * in the current design, so this returns empty string.
     *
     * If we later add a @PermissionMeta annotation to generated fields,
     * this method can read it.
     */
    private static String extractDescription(Field field) {
        // Descriptions are in javadoc comments which aren't available at runtime.
        // For now, return empty. The host application's seeder can maintain
        // descriptions in its own DB table, or we can add a runtime annotation later.
        return "";
    }

    /**
     * Finds all classes ending with "Permissions" in the given package.
     *
     * <p>Uses the current thread's context class loader to scan.
     * This is a classpath scan — it works with standard JAR packaging
     * and most application servers.</p>
     *
     * <p>Note: This implementation uses {@link ServiceLoader} or classpath scanning.
     * For a robust implementation in production, consider using a library like
     * ClassGraph or Spring's classpath scanner. This implementation provides
     * a basic file-system and JAR-based scan.</p>
     */
    private static List<Class<?>> findCompanionClasses(String basePackage) {
        List<Class<?>> result = new ArrayList<>();
        String packagePath = basePackage.replace('.', '/');
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        try {
            var resources = classLoader.getResources(packagePath);
            while (resources.hasMoreElements()) {
                var resource = resources.nextElement();
                String protocol = resource.getProtocol();

                if ("file".equals(protocol)) {
                    scanDirectory(new java.io.File(resource.toURI()), basePackage, result);
                } else if ("jar".equals(protocol)) {
                    scanJar(resource, basePackage, result);
                }
            }
        } catch (Exception e) {
            // If scanning fails, return empty — the host application can use
            // the explicit collect(Class<?>...) overload instead
        }

        return result;
    }

    private static void scanDirectory(java.io.File directory, String packageName,
                                      List<Class<?>> result) {
        if (!directory.exists()) return;

        java.io.File[] files = directory.listFiles();
        if (files == null) return;

        for (java.io.File file : files) {
            if (file.isDirectory()) {
                scanDirectory(file, packageName + "." + file.getName(), result);
            } else if (file.getName().endsWith("Permissions.class")) {
                String className = packageName + "." +
                        file.getName().substring(0, file.getName().length() - 6);
                try {
                    Class<?> clazz = Class.forName(className);
                    result.add(clazz);
                } catch (ClassNotFoundException e) {
                    // Skip
                }
            }
        }
    }

    private static void scanJar(java.net.URL resource, String basePackage,
                                List<Class<?>> result) {
        try {
            String jarPath = resource.getPath();
            // Extract JAR file path from URL like "file:/path/to.jar!/package/path"
            if (jarPath.contains("!")) {
                jarPath = jarPath.substring(0, jarPath.indexOf("!"));
            }
            if (jarPath.startsWith("file:")) {
                jarPath = jarPath.substring(5);
            }

            String packagePath = basePackage.replace('.', '/');

            try (var jarFile = new java.util.jar.JarFile(jarPath)) {
                var entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    var entry = entries.nextElement();
                    String entryName = entry.getName();

                    if (entryName.startsWith(packagePath)
                            && entryName.endsWith("Permissions.class")
                            && !entryName.contains("$")) {

                        String className = entryName
                                .substring(0, entryName.length() - 6)
                                .replace('/', '.');
                        try {
                            Class<?> clazz = Class.forName(className);
                            result.add(clazz);
                        } catch (ClassNotFoundException e) {
                            // Skip
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Skip JAR scanning errors
        }
    }
}