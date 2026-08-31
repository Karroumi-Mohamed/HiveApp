package com.hiveapp.shared.audit;

import com.hiveapp.shared.audit.domain.AuditLog;
import com.hiveapp.shared.audit.domain.AuditLogRepository;
import com.hiveapp.shared.audit.domain.AuditOutcome;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
class AuditFailureRecorder {

    private final AuditLogRepository auditLogRepository;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void record(AuditDraft draft, Throwable failure) {
        auditLogRepository.saveAndFlush(AuditLog.builder()
                .occurredAt(clock.instant())
                .actorSurface(draft.actorSurface())
                .actorUserId(draft.actorUserId())
                .clientAccountId(draft.clientAccountId())
                .targetAccountId(draft.targetAccountId())
                .targetCompanyId(draft.targetCompanyId())
                .collaborationId(draft.collaborationId())
                .action(draft.action())
                .resourceType(draft.resourceType())
                .resourceId(draft.resourceId())
                .outcome(AuditOutcome.FAILED)
                .requestMethod(draft.requestMethod())
                .requestPath(draft.requestPath())
                .requestData(draft.requestData())
                .failureType(failure.getClass().getName())
                .build());
    }
}
