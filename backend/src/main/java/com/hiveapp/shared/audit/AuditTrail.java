package com.hiveapp.shared.audit;

import com.hiveapp.shared.audit.domain.AuditActorSurface;
import com.hiveapp.shared.audit.domain.AuditLog;
import com.hiveapp.shared.audit.domain.AuditLogRepository;
import com.hiveapp.shared.audit.domain.AuditOutcome;
import com.hiveapp.shared.security.context.HiveAppContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditTrail {

    private final AuditLogRepository auditLogRepository;
    private final AuditPayloadSanitizer payloadSanitizer;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordSuccess(
            String action,
            String resourceType,
            UUID resourceId,
            AuditActorSurface actorSurface,
            UUID actorUserId,
            UUID targetAccountId,
            Map<String, ?> before,
            Map<String, ?> after
    ) {
        var context = HiveAppContextHolder.getContext();
        HttpServletRequest request = currentRequest();
        auditLogRepository.save(AuditLog.builder()
                .occurredAt(clock.instant())
                .actorSurface(actorSurface)
                .actorUserId(actorUserId)
                .clientAccountId(context == null ? targetAccountId : context.clientAccountId())
                .targetAccountId(targetAccountId)
                .targetCompanyId(context == null ? null : context.targetCompanyId())
                .collaborationId(context == null ? null : context.collaborationId())
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId == null ? null : resourceId.toString())
                .outcome(AuditOutcome.SUCCEEDED)
                .requestMethod(request == null ? null : request.getMethod())
                .requestPath(request == null ? null : request.getRequestURI())
                .requestData(payloadSanitizer.value(Map.of("before", before == null ? Map.of() : before)))
                .resultData(payloadSanitizer.value(Map.of("after", after == null ? Map.of() : after)))
                .build());
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }
}
