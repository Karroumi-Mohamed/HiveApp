package dev.karroumi.permissionizer;

public final class Permission {
    private final String path;

    /**
     * Creates a permission with the given dot-path.
     *
     * @param path the full dot-separated permission path
     * @throws IllegalArgumentException if path is null or empty
     */
    public Permission(String path) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("Permission path must not be null or empty");
        }
        this.path = path;
    }

    /**
     * The full dot-separated permission path.
     * Used internally for prefix matching and database storage.
     *
     * @return the dot-path (e.g., "platform.client.account.operations.create")
     */
    public String path() {
        return path;
    }

    /**
     * The key (last segment) of this permission's path.
     * For "platform.client.account.operations.create", returns "create".
     *
     * @return the last segment of the path
     */
    public String key() {
        int lastDot = path.lastIndexOf(".");
        return lastDot >= 0 ? path.substring(lastDot + 1) : path;
    }

    /**
     * The depth of this permission in the tree.
     * "platform" is depth 0. "platform.client.account" is depth 2.
     *
     * @return the number of dots in the path
     */
    public int depth() {
        return (int) path.chars().filter(c -> c == '.').count();
    }

    /**
     * Returns true if this permission's path is a prefix of the other's path.
     * "platform.client" is an ancestor of "platform.client.account.operations".
     *
     * @param other the potential descendant permission
     * @return true if this permission is an ancestor of other
     */
    public boolean isAncestorOf(Permission other) {
        return other.path.startsWith(path + ".");
    }

    /**
     * Returns true if this permission's path starts with the other's path.
     * "platform.client.account.operations" is a descendant of "platform.client".
     *
     * @param other the potential ancestor permission
     * @return true if this permission is a descendant of other
     */
    public boolean isDescendantOf(Permission other) {
        return path.startsWith(other.path + ".");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof Permission that))
            return false;
        return path.equals(that.path);
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }

    @Override
    public String toString() {
        return path;
    }
}
