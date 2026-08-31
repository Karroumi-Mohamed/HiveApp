package com.hiveapp.shared.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.hiveapp.shared.audit.domain.AuditActorSurface;
import com.hiveapp.shared.audit.domain.AuditLogRepository;
import com.hiveapp.shared.audit.domain.AuditOutcome;
import com.hiveapp.shared.exception.InvalidStateException;
import com.hiveapp.platform.client.plan.service.CommercialCatalogMutation;
import com.hiveapp.platform.client.plan.service.CommercialCatalogMutationAspect;
import com.hiveapp.platform.client.plan.service.CommercialCatalogVersionService;
import com.hiveapp.shared.security.context.HiveAppContextHolder;
import com.hiveapp.shared.security.context.HiveAppPermissionContext;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import dev.karroumi.permissionizer.PermissionNode;
import dev.karroumi.permissionizer.spring.PermissionInterceptor;
import org.aopalliance.aop.Advice;
import org.springframework.aop.Advisor;
import org.springframework.aop.PointcutAdvisor;
import org.springframework.aop.aspectj.AbstractAspectJAdvice;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuditMutationIntegrationTest extends PlatformShellIntegrationTestSupport {

    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private AuditTestMutation auditTestMutation;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private CatalogOuterMutation catalogOuterMutation;
    @Autowired private CatalogWithoutTransaction catalogWithoutTransaction;
    @Autowired private CommercialCatalogVersionService commercialCatalogVersionService;

    private UUID actorUserId;
    private UUID accountId;
    private UUID companyId;
    private UUID collaborationId;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAllInBatch();
        actorUserId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        collaborationId = UUID.randomUUID();
        HiveAppContextHolder.setContext(new HiveAppPermissionContext(
                actorUserId,
                accountId,
                accountId,
                companyId,
                collaborationId,
                false));
    }

    @AfterEach
    void tearDown() {
        HiveAppContextHolder.clearContext();
        auditLogRepository.deleteAllInBatch();
    }

    @Test
    void successfulMutationRecordsActorScopeResultAndRedactsSecrets() {
        UUID resourceId = UUID.randomUUID();

        UUID result = auditTestMutation.succeed(accountId, resourceId, "must-not-appear");

        assertThat(result).isEqualTo(resourceId);
        assertThat(auditLogRepository.findAll()).singleElement().satisfies(log -> {
            assertThat(log.getOutcome()).isEqualTo(AuditOutcome.SUCCEEDED);
            assertThat(log.getActorSurface()).isEqualTo(AuditActorSurface.CLIENT_WORKSPACE);
            assertThat(log.getActorUserId()).isEqualTo(actorUserId);
            assertThat(log.getClientAccountId()).isEqualTo(accountId);
            assertThat(log.getTargetAccountId()).isEqualTo(accountId);
            assertThat(log.getTargetCompanyId()).isEqualTo(companyId);
            assertThat(log.getCollaborationId()).isEqualTo(collaborationId);
            assertThat(log.getResourceId()).isEqualTo(resourceId.toString());
            assertThat(log.getRequestData()).contains(AuditPayloadSanitizer.REDACTED);
            assertThat(log.getRequestData()).doesNotContain("must-not-appear");
        });
    }

    @Test
    void successfulAuditRollsBackWithTheMutationTransaction() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            auditTestMutation.succeed(accountId, UUID.randomUUID(), "secret");
            throw new InvalidStateException("roll back after the audited operation");
        })).isInstanceOf(InvalidStateException.class);

        assertThat(auditLogRepository.count()).isZero();
    }

    @Test
    void failedAttemptSurvivesTheRejectedMutationRollbackWithoutItsMessage() {
        assertThatThrownBy(() -> auditTestMutation.fail(accountId, "private failure detail"))
                .isInstanceOf(InvalidStateException.class);

        assertThat(auditLogRepository.findAll()).singleElement().satisfies(log -> {
            assertThat(log.getOutcome()).isEqualTo(AuditOutcome.FAILED);
            assertThat(log.getActorUserId()).isEqualTo(actorUserId);
            assertThat(log.getFailureType()).isEqualTo(InvalidStateException.class.getName());
            assertThat(log.getRequestData()).contains(AuditPayloadSanitizer.REDACTED);
            assertThat(log.getRequestData()).doesNotContain("private failure detail");
            assertThat(log.getResultData()).isNull();
        });
    }

    @Test
    void persistedAuditRecordsRejectApplicationDeletion() {
        auditTestMutation.succeed(accountId, UUID.randomUUID(), "secret");
        var persisted = auditLogRepository.findAll().getFirst();

        assertThatThrownBy(() -> auditLogRepository.delete(persisted))
                .hasRootCauseMessage("Audit records are append-only");

        assertThat(auditLogRepository.count()).isOne();
    }

    @Test
    void commercialMutationAdvisorOrderIsTransactionThenAuditThenPermissionThenCatalogThenMethod()
            throws Exception {
        assertThat(auditTestMutation).isInstanceOf(Advised.class);
        Advised advised = (Advised) auditTestMutation;
        Class<?> targetClass = AopUtils.getTargetClass(auditTestMutation);
        var method = targetClass.getMethod("catalogMutation", UUID.class);

        List<String> chain = Arrays.stream(advised.getAdvisors())
                .filter(advisor -> appliesTo(advisor, method, targetClass))
                .map(this::advisorKind)
                .filter(kind -> kind != null)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        chain.add("METHOD");

        assertThat(chain).containsSubsequence(
                "TRANSACTION", "AUDIT", "PERMISSION", "CATALOG", "METHOD");
    }

    @Test
    void nestedCommercialMutationsAdvanceTheRevisionOncePerTransaction() {
        long before = commercialCatalogVersionService.currentRevision();

        catalogOuterMutation.mutate();

        assertThat(commercialCatalogVersionService.currentRevision()).isEqualTo(before + 1);
    }

    @Test
    void rolledBackCommercialMutationDoesNotAdvanceTheRevision() {
        long before = commercialCatalogVersionService.currentRevision();

        assertThatThrownBy(() -> catalogOuterMutation.mutateThenFail())
                .isInstanceOf(InvalidStateException.class);

        assertThat(commercialCatalogVersionService.currentRevision()).isEqualTo(before);
    }

    @Test
    void commercialMutationWithoutATransactionFailsClosed() {
        assertThatThrownBy(catalogWithoutTransaction::mutate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active Spring transaction");
    }

    @Test
    void nestedIndependentCommercialTransactionFailsBeforeLockingAgain() {
        long before = commercialCatalogVersionService.currentRevision();

        assertThatThrownBy(() -> catalogOuterMutation.mutateWithRequiresNew())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("independent nested transaction");

        assertThat(commercialCatalogVersionService.currentRevision()).isEqualTo(before);
    }

    @Test
    void readOnlyFailuresAreOutsideTheMutationAuditBoundary() {
        assertThatThrownBy(() -> auditTestMutation.readOnlyFailure(accountId))
                .isInstanceOf(InvalidStateException.class);

        assertThat(auditLogRepository.count()).isZero();
    }

    @Test
    void realPermissionizerMutationRecordsTheAuthenticatedClientActor() throws Exception {
        HiveAppContextHolder.clearContext();
        String token = registerClientAndGetToken();

        JsonNode company = createCompany(token, "Audited Company " + UUID.randomUUID());
        String companyId = company.get("id").asText();

        assertThat(auditLogRepository.findAll()).anySatisfy(log -> {
            assertThat(log.getAction()).isEqualTo("platform.company.create");
            assertThat(log.getResourceType()).isEqualTo("COMPANY");
            assertThat(log.getResourceId()).isEqualTo(companyId);
            assertThat(log.getActorSurface()).isEqualTo(AuditActorSurface.CLIENT_WORKSPACE);
            assertThat(log.getActorUserId()).isNotNull();
            assertThat(log.getTargetAccountId()).isNotNull();
            assertThat(log.getRequestMethod()).isEqualTo("POST");
            assertThat(log.getRequestPath()).isEqualTo("/api/v1/companies");
        });
    }

    @Test
    void rejectedRealMutationRetainsItsActorAndTargetAttempt() throws Exception {
        HiveAppContextHolder.clearContext();
        String token = registerClientAndGetToken();
        UUID missingCompanyId = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/companies/{id}", missingCompanyId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Missing Company\"}"))
                .andExpect(status().isNotFound());

        assertThat(auditLogRepository.findAll()).anySatisfy(log -> {
            assertThat(log.getAction()).isEqualTo("platform.company.update");
            assertThat(log.getOutcome()).isEqualTo(AuditOutcome.FAILED);
            assertThat(log.getActorSurface()).isEqualTo(AuditActorSurface.CLIENT_WORKSPACE);
            assertThat(log.getActorUserId()).isNotNull();
            assertThat(log.getResourceId()).isEqualTo(missingCompanyId.toString());
            assertThat(log.getFailureType()).contains("ResourceNotFoundException");
        });
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class AuditTestConfiguration {

        @Bean
        AuditTestMutation auditTestMutation() {
            return new AuditTestMutation();
        }

        @Bean
        CatalogInnerMutation catalogInnerMutation() {
            return new CatalogInnerMutation();
        }

        @Bean
        CatalogOuterMutation catalogOuterMutation(CatalogInnerMutation inner) {
            return new CatalogOuterMutation(inner);
        }

        @Bean
        CatalogRequiresNewMutation catalogRequiresNewMutation() {
            return new CatalogRequiresNewMutation();
        }

        @Bean
        CatalogWithoutTransaction catalogWithoutTransaction() {
            return new CatalogWithoutTransaction();
        }
    }

    static class AuditTestMutation {

        @Transactional
        @PermissionNode(key = "succeed", guard = PermissionNode.Guard.OFF)
        public UUID succeed(UUID accountId, UUID resourceId, String password) {
            return resourceId;
        }

        @Transactional
        @PermissionNode(key = "fail", guard = PermissionNode.Guard.OFF)
        public void fail(UUID accountId, String secret) {
            throw new InvalidStateException("must not be persisted");
        }

        @Transactional(readOnly = true)
        @PermissionNode(key = "read_only_failure", guard = PermissionNode.Guard.OFF)
        public void readOnlyFailure(UUID accountId) {
            throw new InvalidStateException("read-only failures are not mutation audit events");
        }

        @Transactional
        @CommercialCatalogMutation
        @PermissionNode(key = "catalog_mutation", guard = PermissionNode.Guard.OFF)
        public void catalogMutation(UUID resourceId) {
            // Advisor-order probe only.
        }
    }

    static class CatalogInnerMutation {

        @Transactional
        @CommercialCatalogMutation
        public void mutate() {
            // Transaction coalescing probe only.
        }
    }

    static class CatalogOuterMutation {

        private final CatalogInnerMutation inner;

        @Autowired
        private CatalogRequiresNewMutation requiresNew;

        CatalogOuterMutation(CatalogInnerMutation inner) {
            this.inner = inner;
        }

        @Transactional
        @CommercialCatalogMutation
        public void mutate() {
            inner.mutate();
        }

        @Transactional
        @CommercialCatalogMutation
        public void mutateThenFail() {
            inner.mutate();
            throw new InvalidStateException("roll back the catalogue mutation");
        }

        @Transactional
        @CommercialCatalogMutation
        public void mutateWithRequiresNew() {
            requiresNew.mutate();
        }
    }

    static class CatalogRequiresNewMutation {

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        @CommercialCatalogMutation
        public void mutate() {
            // The guard must reject this before requesting the singleton lock a second time.
        }
    }

    static class CatalogWithoutTransaction {

        @CommercialCatalogMutation
        public void mutate() {
            // The guard must reject this before entering the method.
        }
    }

    private boolean appliesTo(Advisor advisor, java.lang.reflect.Method method, Class<?> targetClass) {
        return !(advisor instanceof PointcutAdvisor pointcutAdvisor)
                || pointcutAdvisor.getPointcut().getMethodMatcher().matches(method, targetClass);
    }

    private String advisorKind(Advisor advisor) {
        Advice advice = advisor.getAdvice();
        if (advice instanceof TransactionInterceptor) return "TRANSACTION";
        if (advice instanceof AbstractAspectJAdvice aspectJAdvice) {
            Class<?> aspectType = aspectJAdvice.getAspectJAdviceMethod().getDeclaringClass();
            if (aspectType == AuditMutationAspect.class) return "AUDIT";
            if (aspectType == PermissionInterceptor.class) return "PERMISSION";
            if (aspectType == CommercialCatalogMutationAspect.class) return "CATALOG";
        }
        return null;
    }
}
