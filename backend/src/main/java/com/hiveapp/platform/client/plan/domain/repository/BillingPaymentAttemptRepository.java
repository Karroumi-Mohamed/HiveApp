package com.hiveapp.platform.client.plan.domain.repository;

import com.hiveapp.platform.client.plan.domain.constant.BillingPaymentStatus;
import com.hiveapp.platform.client.plan.domain.constant.BillingPaymentKind;
import com.hiveapp.platform.client.plan.domain.entity.BillingPaymentAttempt;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BillingPaymentAttemptRepository extends JpaRepository<BillingPaymentAttempt, UUID> {
    Optional<BillingPaymentAttempt> findByIdempotencyKey(String idempotencyKey);

    Optional<BillingPaymentAttempt> findByExternalReference(String externalReference);

    List<BillingPaymentAttempt> findAllByInvoiceIdOrderByCreatedAtDesc(UUID invoiceId);

    Optional<BillingPaymentAttempt> findFirstByInvoiceIdAndKindOrderByCreatedAtDesc(
            UUID invoiceId,
            BillingPaymentKind kind);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select payment from BillingPaymentAttempt payment join fetch payment.invoice "
            + "where payment.id = :id")
    Optional<BillingPaymentAttempt> findByIdForUpdate(@Param("id") UUID id);

    @Query("select coalesce(sum(payment.amount), 0) from BillingPaymentAttempt payment "
            + "where payment.invoice.id = :invoiceId and payment.status = :status")
    BigDecimal sumAmountByInvoiceIdAndStatus(
            @Param("invoiceId") UUID invoiceId,
            @Param("status") BillingPaymentStatus status);
}
