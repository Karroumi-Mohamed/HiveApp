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
class AuditSuccessRecorder {

    private final AuditLogRepository auditLogRepository;
    private final AuditPayloadSanitizer payloadSanitizer;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    void record(AuditDraft draft, Object result) {
        String resultResourceId = payloadSanitizer.extractResourceId(result);
        auditLogRepository.save(toLog(
                draft,
                resultResourceId == null ? draft.resourceId() : resultResourceId,
                AuditOutcome.SUCCEEDED,
                payloadSanitizer.result(result),
                null));
    }

    private AuditLog toLog(
            AuditDraft draft,
            String resourceId,
            AuditOutcome outcome,
            String resultData,
            String failureType
    ) {
        return AuditLog.builder()
                .occurredAt(clock.instant())
                .actorSurface(draft.actorSurface())
                .actorUserId(draft.actorUserId())
                .clientAccountId(draft.clientAccountId())
                .targetAccountId(draft.targetAccountId())
                .targetCompanyId(draft.targetCompanyId())
                .collaborationId(draft.collaborationId())
                .action(draft.action())
                .resourceType(draft.resourceType())
                .resourceId(resourceId)
                .outcome(outcome)
                .requestMethod(draft.requestMethod())
                .requestPath(draft.requestPath())
                .requestData(draft.requestData())
                .resultData(resultData)
                .failureType(failureType)
                .build();
    }
}
