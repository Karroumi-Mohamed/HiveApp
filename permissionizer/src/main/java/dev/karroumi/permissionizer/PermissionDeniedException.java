package dev.karroumi.permissionizer;

/**
 * Thrown when a permission check fails in {@link PermissionGuard}.
 *
 * <p>Carries the permission path that was required but not granted,
 * allowing the host application to build informative error responses.</p>
 *
 * <h3>Host application exception handler example:</h3>
 * <pre>
 * {@literal @}ExceptionHandler(PermissionDeniedException.class)
 * {@literal @}ResponseStatus(HttpStatus.FORBIDDEN)
 * public ApiError handlePermissionDenied(PermissionDeniedException ex) {
 *     return new ApiError(403, "Forbidden", ex.getMessage());
 * }
 * </pre>
 */
public class PermissionDeniedException extends RuntimeException {

    private final String permission;

    public PermissionDeniedException(String permission) {
        super("Permission denied: " + permission);
        this.permission = permission;
    }

    /**
     * The permission path that was required but not granted.
     *
     * @return the dot-path string (e.g., "platform.client.account.operations.create")
     */
    public String getPermission() {
        return permission;
    }
}