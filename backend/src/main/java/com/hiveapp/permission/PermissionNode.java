package com.hiveapp.permission;

/**
 * Represents a single node in the permission tree.
 * Returned by {@link PermissionCollector} after scanning generated companion classes.
 *
 * @param path       the full dot-path (e.g., "erp.hr.payroll.export")
 * @param description human-readable description for admin UI
 * @param parentPath  the parent's dot-path, or null for root nodes
 */
public record PermissionNode(
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
     * Root nodes have depth 0.
     */
    public int depth() {
        if (path == null || path.isEmpty()) return 0;
        return (int) path.chars().filter(c -> c == '.').count();
    }
}