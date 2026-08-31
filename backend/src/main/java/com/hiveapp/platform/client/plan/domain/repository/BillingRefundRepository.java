package com.hiveapp.platform.client.plan.domain.repository;

import com.hiveapp.platform.client.plan.domain.constant.BillingRefundStatus;
import com.hiveapp.platform.client.plan.domain.entity.BillingRefund;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BillingRefundRepository extends JpaRepository<BillingRefund, UUID> {
    Optional<BillingRefund> findByIdempotencyKey(String idempotencyKey);

    List<BillingRefund> findAllByPaymentInvoiceIdOrderByCreatedAtDesc(UUID invoiceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select refund from BillingRefund refund join fetch refund.payment payment "
            + "join fetch payment.invoice where refund.id = :id")
    Optional<BillingRefund> findByIdForUpdate(@Param("id") UUID id);

    @Query("select coalesce(sum(refund.amount), 0) from BillingRefund refund "
            + "where refund.payment.id = :paymentId and refund.status = :status")
    BigDecimal sumAmountByPaymentIdAndStatus(
            @Param("paymentId") UUID paymentId,
            @Param("status") BillingRefundStatus status);

    @Query("select coalesce(sum(refund.amount), 0) from BillingRefund refund "
            + "where refund.payment.id = :paymentId and refund.status in :statuses")
    BigDecimal sumAmountByPaymentIdAndStatusIn(
            @Param("paymentId") UUID paymentId,
            @Param("statuses") List<BillingRefundStatus> statuses);
}
