package com.hiveapp.platform.client.plan.domain.repository;

import com.hiveapp.platform.client.plan.domain.entity.BillingCredit;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BillingCreditRepository extends JpaRepository<BillingCredit, UUID> {
    List<BillingCredit> findAllByInvoiceIdOrderByCreatedAtDesc(UUID invoiceId);

    @Query("select coalesce(sum(credit.amount), 0) from BillingCredit credit "
            + "where credit.invoice.id = :invoiceId")
    BigDecimal sumAmountByInvoiceId(@Param("invoiceId") UUID invoiceId);
}
