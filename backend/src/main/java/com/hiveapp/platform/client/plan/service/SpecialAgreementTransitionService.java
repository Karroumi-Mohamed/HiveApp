package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.SpecialAgreementAttentionStage;
import com.hiveapp.platform.client.plan.domain.constant.SpecialAgreementStatus;
import com.hiveapp.platform.client.plan.domain.entity.SpecialCommercialAgreement;
import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.domain.entity.SubscriptionChangeOperation;
import com.hiveapp.platform.client.plan.domain.repository.SpecialCommercialAgreementRepository;
import com.hiveapp.shared.audit.AuditTrail;
import com.hiveapp.shared.audit.domain.AuditActorSurface;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Keeps agreement state synchronized with its ordinary subscription operation. */
@Service
@RequiredArgsConstructor
public class SpecialAgreementTransitionService {
    private final SpecialCommercialAgreementRepository agreements;
    private final AuditTrail auditTrail;
    private final Clock clock;

    public Optional<SpecialCommercialAgreement> find(UUID operationId) {
        return agreements.findByChangeOperationId(operationId);
    }

    public void operationScheduled(SubscriptionChangeOperation operation) {
        find(operation.getId()).ifPresent(agreement -> {
            agreement.setStatus(SpecialAgreementStatus.SCHEDULED);
            agreement.setAttentionStage(null);
            agreement.setAttentionReason(null);
            agreements.save(agreement);
        });
    }

    public void operationApplied(SubscriptionChangeOperation operation, Subscription result) {
        find(operation.getId()).ifPresent(agreement -> {
            agreement.setStatus(SpecialAgreementStatus.ACTIVE);
            agreement.setResultSubscription(result);
            agreement.setActivatedAt(clock.instant());
            agreement.setAttentionStage(null);
            agreement.setAttentionReason(null);
            agreements.save(agreement);
            recordStartAttempt(agreement, "ACTIVE", null);
        });
    }

    public void operationNeedsAttention(SubscriptionChangeOperation operation, String reason) {
        find(operation.getId()).ifPresent(agreement -> {
            agreement.setStatus(SpecialAgreementStatus.NEEDS_ATTENTION);
            agreement.setAttentionStage(SpecialAgreementAttentionStage.START);
            agreement.setAttentionReason(reason);
            agreements.save(agreement);
            recordStartAttempt(agreement, "NEEDS_ATTENTION", reason);
        });
    }

    private void recordStartAttempt(
            SpecialCommercialAgreement agreement, String outcome, String reason) {
        var evidence = new java.util.LinkedHashMap<String, Object>();
        evidence.put("stage", "START");
        evidence.put("outcome", outcome);
        evidence.put("attemptedAt", clock.instant().toString());
        if (reason != null && !reason.isBlank()) evidence.put("reason", reason);
        auditTrail.recordSuccess(
                "platform.client.subscription.special_agreement.start_attempt",
                "SPECIAL_COMMERCIAL_AGREEMENT", agreement.getId(), AuditActorSurface.SYSTEM,
                null, agreement.getAccount().getId(), Map.of(), evidence);
    }
}
