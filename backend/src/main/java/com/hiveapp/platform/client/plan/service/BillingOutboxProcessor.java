package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.BillingOutboxStatus;
import com.hiveapp.platform.client.plan.domain.repository.BillingOutboxCommandRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BillingOutboxProcessor {
    private static final int BATCH_SIZE = 50;
    private static final Duration STALE_CLAIM = Duration.ofMinutes(5);

    private final BillingOutboxCommandRepository commands;
    private final BillingOutboxTransactionService transactions;
    private final BillingProviderCommandExecutor executor;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${hiveapp.billing.outbox-delay-ms:15000}")
    public void processDue() {
        processDue(clock.instant());
    }

    public void processDue(Instant cutoff) {
        recoverStale(cutoff.minus(STALE_CLAIM));
        List<UUID> ids = commands.findReadyIds(
                BillingOutboxStatus.PENDING, cutoff, PageRequest.of(0, BATCH_SIZE));
        for (UUID id : ids) processOne(id);
    }

    void processOne(UUID id) {
        var claimed = transactions.claim(id);
        if (claimed.isEmpty()) return;
        try {
            var result = executor.execute(claimed.get());
            transactions.complete(id, result);
        } catch (RuntimeException failure) {
            transactions.failOrRetry(id, failure);
            log.warn("Billing provider command {} failed and was retained for reconciliation", id);
        }
    }

    private void recoverStale(Instant staleBefore) {
        List<UUID> ids = commands.findStaleClaimIds(
                BillingOutboxStatus.PROCESSING, staleBefore, PageRequest.of(0, BATCH_SIZE));
        for (UUID id : ids) transactions.recoverStale(id, staleBefore);
    }
}
