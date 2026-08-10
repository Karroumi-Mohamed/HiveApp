package com.hiveapp.shared.audit;

import dev.karroumi.permissionizer.PermissionNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Aspect
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@Slf4j
public class AuditMutationAspect {

    private final AuditDraftFactory draftFactory;
    private final AuditSuccessRecorder successRecorder;
    private final AuditFailureRecorder failureRecorder;

    @Around("@annotation(transactional) && @annotation(permissionNode)")
    public Object auditMutation(
            ProceedingJoinPoint joinPoint,
            Transactional transactional,
            PermissionNode permissionNode
    ) throws Throwable {
        if (transactional.readOnly()) return joinPoint.proceed();

        return audit(joinPoint, draftFactory.create(joinPoint, permissionNode));
    }

    @Around("@annotation(transactional) && @annotation(auditedMutation)")
    public Object auditInternalMutation(
            ProceedingJoinPoint joinPoint,
            Transactional transactional,
            AuditedMutation auditedMutation
    ) throws Throwable {
        if (transactional.readOnly()) return joinPoint.proceed();
        return audit(
                joinPoint,
                draftFactory.create(joinPoint, auditedMutation),
                auditedMutation.recordSuccess(),
                auditedMutation.recordFailure());
    }

    private Object audit(ProceedingJoinPoint joinPoint, AuditDraft draft) throws Throwable {
        return audit(joinPoint, draft, true, true);
    }

    private Object audit(
            ProceedingJoinPoint joinPoint,
            AuditDraft draft,
            boolean recordSuccess,
            boolean recordFailure
    ) throws Throwable {
        try {
            Object result = joinPoint.proceed();
            if (recordSuccess) successRecorder.record(draft, result);
            return result;
        } catch (Throwable failure) {
            if (recordFailure) {
                try {
                    failureRecorder.record(draft, failure);
                } catch (RuntimeException auditFailure) {
                    log.error("Failed to persist rejected mutation audit record for {}", draft.action(), auditFailure);
                }
            }
            throw failure;
        }
    }
}
