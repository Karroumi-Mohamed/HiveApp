package com.hiveapp.platform.admin.dto;

import com.hiveapp.shared.exception.ErrorCode;
import java.util.List;
import java.util.UUID;

/**
 * Outcome of an operation applied to many rows at once.
 *
 * <p>Partial failure is the normal case here, not an edge case: an administrator may not
 * deactivate their own account, and only a SuperAdmin may modify another SuperAdmin. Selecting a
 * page of operators and deactivating them will therefore routinely succeed for most and fail for
 * a few. A single HTTP status cannot express that, so each rejection is reported individually
 * with the reason that applies to it.
 *
 * <p>Every item runs in its own transaction, so a rejection never rolls back the rows that
 * succeeded.
 */
public record BulkOperationResult(
        int requested,
        int succeeded,
        List<Failure> failures
) {
    public record Failure(UUID id, ErrorCode code, String message) {}

    public static BulkOperationResult of(int requested, List<Failure> failures) {
        return new BulkOperationResult(requested, requested - failures.size(), List.copyOf(failures));
    }
}
