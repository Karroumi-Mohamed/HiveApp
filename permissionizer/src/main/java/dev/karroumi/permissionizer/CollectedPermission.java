package dev.karroumi.permissionizer;

/**
 * Represents a single permission node collected from generated classes.
 * Returned by {@link PermissionCollector} for use in database seeding,
 * admin UI rendering, orphan detection, or documentation generation.
 *
 * @param path       the full dot-path (e.g., "platform.client.account.operations.create")
 * @param description human-readable description for admin UI, may be empty
 * @param parentPath  the parent's dot-path, or null for root nodes
 */
public record CollectedPermission(
        String path,
        String description,
        String parentPath
) {

    /**
     * Returns the key (last segment) of this node's path.
     * For "erp.hr.payroll.export", returns "export".
     */
    public String key() {
        int lastDot = path.lastIndexOf('.');
        return lastDot >= 0 ? path.substring(lastDot + 1) : path;
    }

    /**
     * Returns true if this is a root node (no parent).
     */
    public boolean isRoot() {
        return parentPath == null;
    }

    /**
     * Returns the depth of this node in the tree.
     * Root nodes have depth 0. "a.b.c" has depth 2.
     */
    public int depth() {
        if (path == null || path.isEmpty()) return 0;
        return (int) path.chars().filter(c -> c == '.').count();
    }
}