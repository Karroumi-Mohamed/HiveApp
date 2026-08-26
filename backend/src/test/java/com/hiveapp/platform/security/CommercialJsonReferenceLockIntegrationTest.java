package com.hiveapp.platform.security;

import com.hiveapp.platform.client.plan.domain.constant.AddOnStatus;
import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.PlanStatus;
import com.hiveapp.platform.client.plan.domain.constant.QuotaPackageStatus;
import com.hiveapp.platform.client.plan.domain.entity.AddOn;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import com.hiveapp.platform.client.plan.domain.entity.QuotaPackage;
import com.hiveapp.platform.client.plan.domain.repository.AddOnRepository;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.domain.repository.QuotaPackageRepository;
import com.hiveapp.platform.registry.domain.repository.FeatureRepository;
import com.hiveapp.shared.money.Money;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class CommercialJsonReferenceLockIntegrationTest extends PlatformShellIntegrationTestSupport {

    @Autowired private PlanRepository planRepository;
    @Autowired private AddOnRepository addOnRepository;
    @Autowired private QuotaPackageRepository quotaPackageRepository;
    @Autowired private FeatureRepository featureRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void concurrentPlanDeleteAndAddOnReferenceWriteCannotLeaveADanglingCode() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Plan target = planRepository.saveAndFlush(plan("LOCK_PLAN_" + suffix));
        AddOn referrer = addOnRepository.saveAndFlush(addOn("LOCK_ADDON_" + suffix));
        AtomicBoolean referenceRejected = new AtomicBoolean();
        AtomicBoolean deletionCommitted = new AtomicBoolean();

        runDeleteAgainstReferenceWriter(
                () -> planRepository.findByIdForUpdate(target.getId()).orElseThrow(),
                () -> {
                    var locked = planRepository.findAllByCodeInForUpdate(Set.of(target.getCode()));
                    if (locked.isEmpty()) {
                        referenceRejected.set(true);
                        return;
                    }
                    AddOn current = addOnRepository.findByIdForUpdate(referrer.getId()).orElseThrow();
                    current.setAllowedPlanCodes(Set.of(target.getCode()));
                    addOnRepository.saveAndFlush(current);
                },
                () -> {
                    planRepository.deleteById(target.getId());
                    planRepository.flush();
                },
                referenceRejected,
                deletionCommitted);

        assertThat(referenceRejected).isTrue();
        assertThat(deletionCommitted).isTrue();
        assertThat(planRepository.findById(target.getId())).isEmpty();
        assertThat(addOnRepository.findById(referrer.getId()).orElseThrow().getAllowedPlanCodes())
                .doesNotContain(target.getCode());
        addOnRepository.deleteById(referrer.getId());
        addOnRepository.flush();
    }

    @Test
    void concurrentAddOnDeleteAndQuotaReferenceWriteCannotLeaveADanglingCodeOrDeadlock()
            throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        AddOn target = addOnRepository.saveAndFlush(addOn("LOCK_TARGET_" + suffix));
        QuotaPackage referrer = quotaPackageRepository.saveAndFlush(quotaPackage("LOCK_QUOTA_" + suffix));
        AtomicBoolean referenceRejected = new AtomicBoolean();
        AtomicBoolean deletionCommitted = new AtomicBoolean();

        runDeleteAgainstReferenceWriter(
                () -> addOnRepository.findByIdForUpdate(target.getId()).orElseThrow(),
                () -> {
                    var locked = addOnRepository.findAllByCodeInForUpdate(Set.of(target.getCode()));
                    if (locked.isEmpty()) {
                        referenceRejected.set(true);
                        return;
                    }
                    QuotaPackage current = quotaPackageRepository
                            .findByIdForUpdate(referrer.getId()).orElseThrow();
                    current.setAllowedAddOnCodes(Set.of(target.getCode()));
                    quotaPackageRepository.saveAndFlush(current);
                },
                () -> {
                    addOnRepository.deleteById(target.getId());
                    addOnRepository.flush();
                },
                referenceRejected,
                deletionCommitted);

        assertThat(referenceRejected).isTrue();
        assertThat(deletionCommitted).isTrue();
        assertThat(addOnRepository.findById(target.getId())).isEmpty();
        assertThat(quotaPackageRepository.findById(referrer.getId()).orElseThrow()
                .getAllowedAddOnCodes()).doesNotContain(target.getCode());
        quotaPackageRepository.deleteById(referrer.getId());
        quotaPackageRepository.flush();
    }

    private void runDeleteAgainstReferenceWriter(
            Runnable acquireTargetLock,
            Runnable writeReference,
            Runnable deleteTarget,
            AtomicBoolean referenceRejected,
            AtomicBoolean deletionCommitted
    ) throws Exception {
        CountDownLatch targetLocked = new CountDownLatch(1);
        CountDownLatch writerEnteringLock = new CountDownLatch(1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var delete = executor.submit(() -> transaction.executeWithoutResult(status -> {
                acquireTargetLock.run();
                targetLocked.countDown();
                await(writerEnteringLock);
                deleteTarget.run();
                deletionCommitted.set(true);
            }));
            var writer = executor.submit(() -> {
                await(targetLocked);
                try {
                    transaction.executeWithoutResult(status -> {
                        writerEnteringLock.countDown();
                        writeReference.run();
                    });
                } catch (ObjectOptimisticLockingFailureException staleReference) {
                    // The delete committed first. HTTP callers receive the globally mapped stable
                    // 409 STALE_RESOURCE_VERSION rather than a dangling JSON reference or a 500.
                    referenceRejected.set(true);
                }
            });

            delete.get(5, TimeUnit.SECONDS);
            writer.get(5, TimeUnit.SECONDS);
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Concurrent lock test timed out.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Concurrent lock test was interrupted.", exception);
        }
    }

    private Plan plan(String code) {
        Plan plan = new Plan();
        plan.setCode(code);
        plan.setName(code);
        plan.setMoney(Money.zero("USD"));
        plan.setBillingCycle(BillingCycle.MONTHLY);
        plan.setStatus(PlanStatus.DRAFT);
        plan.setLineageId(UUID.randomUUID());
        plan.setRevisionNumber(1);
        return plan;
    }

    private AddOn addOn(String code) {
        AddOn addOn = new AddOn();
        addOn.setCode(code);
        addOn.setName(code);
        addOn.setMoney(Money.of(BigDecimal.ONE, "USD"));
        addOn.setBillingCycle(BillingCycle.MONTHLY);
        addOn.setStatus(AddOnStatus.DRAFT);
        addOn.setLineageId(UUID.randomUUID());
        addOn.setRevisionNumber(1);
        addOn.setAllowedPlanCodes(Set.of());
        addOn.setBlockedPlanCodes(Set.of());
        addOn.setDependencyCodes(Set.of());
        addOn.setExclusionCodes(Set.of());
        return addOn;
    }

    private QuotaPackage quotaPackage(String code) {
        QuotaPackage item = new QuotaPackage();
        item.setCode(code);
        item.setName(code);
        item.setFeature(featureRepository.findByCode("platform.staff").orElseThrow());
        item.setResource("members");
        item.setCapacityPerUnit(1);
        item.setMoney(Money.of(BigDecimal.ONE, "USD"));
        item.setBillingCycle(BillingCycle.MONTHLY);
        item.setRepeatable(true);
        item.setMaximumQuantity(10);
        item.setAllowedPlanCodes(Set.of());
        item.setAllowedAddOnCodes(Set.of());
        item.setStatus(QuotaPackageStatus.DRAFT);
        item.setLineageId(UUID.randomUUID());
        item.setRevisionNumber(1);
        return item;
    }
}
