package com.hiveapp.platform.client.plan.service;

import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Acquires the catalogue singleton before any product/feature/price lock. Nested REQUIRED calls
 * share one guard and one revision increment. A nested independent transaction is rejected before
 * it can wait on the outer transaction's singleton lock and deadlock the calling thread.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Order(Ordered.LOWEST_PRECEDENCE)
public class CommercialCatalogMutationAspect {

    private final CommercialCatalogVersionService versionService;
    private final EntityManagerFactory entityManagerFactory;
    private final ThreadLocal<Map<Object, MutationContext>> contexts =
            ThreadLocal.withInitial(IdentityHashMap::new);

    @Around("@annotation(com.hiveapp.platform.client.plan.service.CommercialCatalogMutation)")
    public Object serialize(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException(
                    "Commercial catalogue mutations require an active Spring transaction");
        }

        Object transactionKey = TransactionSynchronizationManager.getResource(entityManagerFactory);
        if (transactionKey == null) {
            throw new IllegalStateException(
                    "Commercial catalogue mutation cannot resolve its Spring transaction resource");
        }
        Map<Object, MutationContext> active = contexts.get();
        MutationContext context = active.get(transactionKey);
        if (context == null) {
            if (!active.isEmpty()) {
                throw new IllegalStateException(
                        "A commercial catalogue mutation cannot start an independent nested transaction");
            }
            UUID revisionId = versionService.lockForMutation().getId();
            context = new MutationContext(revisionId);
            active.put(transactionKey, context);
            Object cleanupKey = transactionKey;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    Map<Object, MutationContext> current = contexts.get();
                    current.remove(cleanupKey);
                    if (current.isEmpty()) contexts.remove();
                }
            });
        }

        Object result = joinPoint.proceed();
        if (!context.bumped) {
            versionService.bump(context.revisionId);
            context.bumped = true;
        }
        return result;
    }

    private static final class MutationContext {
        private final UUID revisionId;
        private boolean bumped;

        private MutationContext(UUID revisionId) {
            this.revisionId = revisionId;
        }
    }
}
