package com.hiveapp.platform.client.plan.domain.repository;

import com.hiveapp.platform.client.plan.domain.constant.SpecialAgreementStatus;
import com.hiveapp.platform.client.plan.domain.entity.SpecialCommercialAgreement;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpecialCommercialAgreementRepository
        extends JpaRepository<SpecialCommercialAgreement, UUID> {

    @EntityGraph(attributePaths = {"account", "targetPlan", "changeOperation",
            "changeOperation.checkout", "resultSubscription"})
    Page<SpecialCommercialAgreement> findAllByAccountId(UUID accountId, Pageable pageable);

    @EntityGraph(attributePaths = {"account", "targetPlan", "changeOperation",
            "changeOperation.checkout", "resultSubscription"})
    Page<SpecialCommercialAgreement> findAllByAccountIdAndStatus(
            UUID accountId, SpecialAgreementStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"account", "targetPlan", "changeOperation",
            "changeOperation.checkout", "resultSubscription"})
    @Query("select agreement from SpecialCommercialAgreement agreement "
            + "where (:status is null or agreement.status = :status) "
            + "and (:search is null or lower(agreement.account.name) like :search "
            + "or lower(agreement.targetPlan.name) like :search "
            + "or lower(agreement.targetPlan.code) like :search)")
    Page<SpecialCommercialAgreement> searchAll(
            @Param("status") SpecialAgreementStatus status,
            @Param("search") String search,
            Pageable pageable);

    @EntityGraph(attributePaths = {"account", "sourceSubscription", "targetPlan", "changeOperation",
            "changeOperation.checkout", "resultSubscription"})
    Optional<SpecialCommercialAgreement> findByIdAndAccountId(UUID id, UUID accountId);

    Optional<SpecialCommercialAgreement> findByChangeOperationId(UUID operationId);

    Optional<SpecialCommercialAgreement> findByResultSubscriptionIdAndStatus(
            UUID subscriptionId, SpecialAgreementStatus status);

    boolean existsByAccountIdAndStatusIn(UUID accountId, Collection<SpecialAgreementStatus> statuses);

    long countByStatus(SpecialAgreementStatus status);

    long countByPricingMode(
            com.hiveapp.platform.client.plan.domain.constant.SpecialAgreementPricingMode pricingMode);

    long countBySettlementMode(
            com.hiveapp.platform.client.plan.domain.constant.SpecialAgreementSettlementMode settlementMode);

    @Query("select agreement.currencyCode, "
            + "coalesce(sum(case when agreement.status <> com.hiveapp.platform.client.plan.domain.constant.SpecialAgreementStatus.CANCELLED "
            + "then agreement.catalogueTermAmount else 0 end), 0), "
            + "coalesce(sum(case when agreement.status <> com.hiveapp.platform.client.plan.domain.constant.SpecialAgreementStatus.CANCELLED "
            + "then agreement.agreedTermAmount else 0 end), 0), "
            + "coalesce(sum(case when agreement.status <> com.hiveapp.platform.client.plan.domain.constant.SpecialAgreementStatus.CANCELLED "
            + "and agreement.pricingMode = com.hiveapp.platform.client.plan.domain.constant.SpecialAgreementPricingMode.COMPLIMENTARY "
            + "then agreement.catalogueTermAmount else 0 end), 0), "
            + "coalesce(sum(case when invoice.status <> com.hiveapp.platform.client.plan.domain.constant.BillingInvoiceStatus.CANCELLED "
            + "then invoice.totalAmount else 0 end), 0), "
            + "coalesce(sum(case when invoice.status = com.hiveapp.platform.client.plan.domain.constant.BillingInvoiceStatus.SETTLED "
            + "then invoice.totalAmount else 0 end), 0) "
            + "from SpecialCommercialAgreement agreement "
            + "left join BillingInvoice invoice on invoice.changeOperationId = agreement.changeOperation.id "
            + "group by agreement.currencyCode order by agreement.currencyCode")
    List<Object[]> aggregateMoneyByCurrency();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"account", "sourceSubscription", "targetPlan", "changeOperation",
            "changeOperation.checkout", "resultSubscription"})
    @Query("select agreement from SpecialCommercialAgreement agreement where agreement.id = :id")
    Optional<SpecialCommercialAgreement> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"account", "sourceSubscription", "targetPlan", "changeOperation",
            "resultSubscription"})
    @Query("select agreement from SpecialCommercialAgreement agreement "
            + "where agreement.status = :status and agreement.endsAt <= :cutoff")
    List<SpecialCommercialAgreement> findDueForCompletion(
            @Param("status") SpecialAgreementStatus status,
            @Param("cutoff") Instant cutoff);
}
