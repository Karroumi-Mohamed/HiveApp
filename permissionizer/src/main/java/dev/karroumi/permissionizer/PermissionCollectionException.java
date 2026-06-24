package dev.karroumi.permissionizer;

/**
 * Signals that Permissionizer could not build a complete, trustworthy
 * permission catalog from its generated metadata.
 */
public final class PermissionCollectionException extends IllegalStateException {

    public PermissionCollectionException(String message) {
        super(message);
    }

    public PermissionCollectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
