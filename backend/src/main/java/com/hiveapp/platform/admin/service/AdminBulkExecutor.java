package com.hiveapp.platform.admin.service;

import com.hiveapp.platform.admin.dto.BulkOperationResult;
import com.hiveapp.shared.exception.ErrorCodes;
import com.hiveapp.shared.transaction.IsolatedOperationRunner;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Applies one operation to many targets, isolating each and collecting the rejections.
 *
 * <p>Every bulk endpoint routes through here so the same rules hold everywhere: duplicates are
 * collapsed once, each target runs in its own transaction, and a rejection is reported rather
 * than aborting the batch.
 */
@Component
@RequiredArgsConstructor
public class AdminBulkExecutor {

    private final IsolatedOperationRunner isolatedOperationRunner;

    /**
     * @param ids targets as supplied by the caller; duplicates are collapsed before anything runs
     * @param operation applied to one target, may throw to reject it
     */
    public BulkOperationResult run(List<UUID> ids, Consumer<UUID> operation) {
        // Collapsed in order, once, before any work: a repeated id would otherwise resend a
        // second activation email that invalidates the first link, or report the same operator as
        // both succeeded and already-assigned. It would also make requested/succeeded count
        // attempts rather than targets.
        List<UUID> targets = ids.stream().distinct().toList();

        List<BulkOperationResult.Failure> failures = new ArrayList<>();
        for (UUID id : targets) {
            try {
                isolatedOperationRunner.run(() -> operation.accept(id));
            } catch (RuntimeException rejection) {
                failures.add(new BulkOperationResult.Failure(
                        id, ErrorCodes.of(rejection), rejection.getMessage()));
            }
        }
        return BulkOperationResult.of(targets.size(), failures);
    }
}
