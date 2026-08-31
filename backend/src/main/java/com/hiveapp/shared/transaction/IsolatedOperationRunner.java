package com.hiveapp.shared.transaction;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs one item of a bulk operation in its own transaction.
 *
 * <p>A rejected item must not roll back the items that already succeeded, and a failed write
 * leaves the surrounding transaction marked rollback-only — so each item needs genuine
 * isolation rather than a try/catch inside one shared transaction.
 *
 * <p>Deliberately a separate bean. Calling a {@code @Transactional} method on {@code this}
 * bypasses the Spring proxy entirely, so the propagation would be silently ignored and every
 * item would join the caller's transaction after all.
 */
@Component
public class IsolatedOperationRunner {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void run(Runnable operation) {
        operation.run();
    }
}
