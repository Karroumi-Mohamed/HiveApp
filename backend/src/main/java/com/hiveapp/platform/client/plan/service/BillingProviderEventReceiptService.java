package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.BillingProviderEventStatus;
import com.hiveapp.platform.client.plan.domain.entity.BillingProviderEvent;
import com.hiveapp.platform.client.plan.domain.model.VerifiedBillingProviderEvent;
import com.hiveapp.platform.client.plan.domain.repository.BillingProviderEventRepository;
import com.hiveapp.shared.exception.IdempotencyConflictException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BillingProviderEventReceiptService {
    private final BillingProviderEventRepository events;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RecordedEvent record(VerifiedBillingProviderEvent evidence) {
        var existing = events.findByProviderAndEventId(evidence.provider(), evidence.eventId());
        if (existing.isPresent()) return validateDuplicate(existing.get(), evidence);
        BillingProviderEvent saved = events.saveAndFlush(BillingProviderEvent.received(evidence));
        return new RecordedEvent(saved.getId(), true, saved.getProcessingStatus());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public RecordedEvent resolveConcurrentDuplicate(VerifiedBillingProviderEvent evidence) {
        BillingProviderEvent existing = events.findByProviderAndEventId(
                        evidence.provider(), evidence.eventId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BillingProviderEvent", "provider/eventId",
                        evidence.provider() + "/" + evidence.eventId()));
        return validateDuplicate(existing, evidence);
    }

    private RecordedEvent validateDuplicate(
            BillingProviderEvent existing,
            VerifiedBillingProviderEvent evidence
    ) {
        if (!existing.getPayloadDigest().equals(evidence.payloadDigest())) {
            throw new IdempotencyConflictException();
        }
        return new RecordedEvent(existing.getId(), false, existing.getProcessingStatus());
    }

    public record RecordedEvent(
            UUID id,
            boolean created,
            BillingProviderEventStatus status
    ) {}
}
