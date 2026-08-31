package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.BillingProviderEventStatus;
import com.hiveapp.platform.client.plan.domain.model.VerifiedBillingProviderEvent;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/** Provider adapters call this only after authenticating and normalizing a callback. */
@Service
@RequiredArgsConstructor
public class BillingProviderEventIngress {
    private final BillingProviderEventReceiptService receipts;
    private final BillingOutboxTransactionService transactions;

    public IngestedProviderEvent ingest(VerifiedBillingProviderEvent evidence) {
        BillingProviderEventReceiptService.RecordedEvent recorded;
        try {
            recorded = receipts.record(evidence);
        } catch (DataIntegrityViolationException concurrentInsert) {
            recorded = receipts.resolveConcurrentDuplicate(evidence);
        }
        if (recorded.created() || recorded.status() == BillingProviderEventStatus.RECEIVED) {
            transactions.reconcileProviderEvent(recorded.id());
        }
        return new IngestedProviderEvent(recorded.id(), !recorded.created());
    }

    public record IngestedProviderEvent(UUID eventId, boolean duplicate) {}
}
