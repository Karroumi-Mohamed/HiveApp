package com.hiveapp.platform.client.plan.domain.repository;

import com.hiveapp.platform.client.plan.domain.entity.BillingInvoice;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BillingInvoiceRepository extends JpaRepository<BillingInvoice, UUID> {
    @EntityGraph(attributePaths = {"lines", "checkout", "account"})
    Optional<BillingInvoice> findByCheckoutId(UUID checkoutId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invoice from BillingInvoice invoice join fetch invoice.checkout "
            + "where invoice.checkout.id = :checkoutId")
    Optional<BillingInvoice> findByCheckoutIdForUpdate(@Param("checkoutId") UUID checkoutId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invoice from BillingInvoice invoice where invoice.id = :id")
    Optional<BillingInvoice> findByIdForUpdate(@Param("id") UUID id);
}
