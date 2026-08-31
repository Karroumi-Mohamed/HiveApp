package com.hiveapp.platform.client.plan.domain.repository;

import com.hiveapp.platform.client.plan.domain.entity.BillingInvoice;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BillingInvoiceRepository extends JpaRepository<BillingInvoice, UUID>,
        JpaSpecificationExecutor<BillingInvoice> {
    @Override
    @EntityGraph(attributePaths = "account")
    Page<BillingInvoice> findAll(Specification<BillingInvoice> specification, Pageable pageable);

    @EntityGraph(attributePaths = {"lines", "checkout", "account"})
    Optional<BillingInvoice> findByCheckoutId(UUID checkoutId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invoice from BillingInvoice invoice join fetch invoice.checkout "
            + "where invoice.checkout.id = :checkoutId")
    Optional<BillingInvoice> findByCheckoutIdForUpdate(@Param("checkoutId") UUID checkoutId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invoice from BillingInvoice invoice where invoice.id = :id")
    Optional<BillingInvoice> findByIdForUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"lines", "checkout", "account"})
    @Query("select invoice from BillingInvoice invoice where invoice.id = :id")
    Optional<BillingInvoice> findDetailedById(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"lines", "checkout", "account"})
    @Query("select invoice from BillingInvoice invoice "
            + "where invoice.id = :id and invoice.account.id = :accountId")
    Optional<BillingInvoice> findDetailedByIdAndAccountId(
            @Param("id") UUID id,
            @Param("accountId") UUID accountId);

    org.springframework.data.domain.Page<BillingInvoice> findAllByAccountId(
            UUID accountId,
            org.springframework.data.domain.Pageable pageable);
}
