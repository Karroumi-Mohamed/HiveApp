package com.hiveapp.platform.client.plan.domain.repository;

import com.hiveapp.platform.client.plan.domain.entity.BillingInvoice;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface BillingFinancialTimelineRepository extends Repository<BillingInvoice, UUID> {

    interface EntryProjection {
        String getRecordId();
        String getEntryType();
        String getEntryStatus();
        BigDecimal getAmount();
        String getCurrencyCode();
        String getInvoiceId();
        String getInvoiceNumber();
        OffsetDateTime getOccurredAt();
    }

    @Query(value = """
            select cast(timeline.record_id as varchar(36)) as recordId,
                   timeline.entry_type as entryType,
                   timeline.entry_status as entryStatus,
                   timeline.amount as amount,
                   timeline.currency_code as currencyCode,
                   cast(timeline.invoice_id as varchar(36)) as invoiceId,
                   timeline.invoice_number as invoiceNumber,
                   timeline.occurred_at as occurredAt
              from (
                    select invoice.id as record_id, 'INVOICE' as entry_type,
                           invoice.status as entry_status, invoice.total_amount as amount,
                           invoice.currency_code, invoice.id as invoice_id,
                           invoice.invoice_number, invoice.issued_at as occurred_at,
                           invoice.account_id
                      from billing_invoices invoice
                    union all
                    select payment.id as record_id, 'PAYMENT' as entry_type,
                           payment.status as entry_status, payment.amount,
                           payment.currency_code, invoice.id as invoice_id,
                           invoice.invoice_number,
                           coalesce(payment.completed_at, payment.created_at) as occurred_at,
                           invoice.account_id
                      from billing_payment_attempts payment
                      join billing_invoices invoice on invoice.id = payment.invoice_id
                    union all
                    select credit.id as record_id, 'CREDIT' as entry_type,
                           'ISSUED' as entry_status, credit.amount,
                           credit.currency_code, invoice.id as invoice_id,
                           invoice.invoice_number, credit.issued_at as occurred_at,
                           invoice.account_id
                      from billing_credits credit
                      join billing_invoices invoice on invoice.id = credit.invoice_id
                    union all
                    select refund.id as record_id, 'REFUND' as entry_type,
                           refund.status as entry_status, refund.amount,
                           refund.currency_code, invoice.id as invoice_id,
                           invoice.invoice_number,
                           coalesce(refund.completed_at, refund.created_at) as occurred_at,
                           invoice.account_id
                      from billing_refunds refund
                      join billing_payment_attempts payment on payment.id = refund.payment_id
                      join billing_invoices invoice on invoice.id = payment.invoice_id
                   ) timeline
             where timeline.account_id = :accountId
               and (:entryType is null or timeline.entry_type = :entryType)
               and (:currencyCode is null or timeline.currency_code = :currencyCode)
               and (:occurredFrom is null or timeline.occurred_at >= :occurredFrom)
               and (:occurredUntil is null or timeline.occurred_at < :occurredUntil)
             order by timeline.occurred_at desc, timeline.record_id desc
            """,
            countQuery = """
            select count(*)
              from (
                    select invoice.id as record_id, 'INVOICE' as entry_type,
                           invoice.currency_code, invoice.issued_at as occurred_at,
                           invoice.account_id
                      from billing_invoices invoice
                    union all
                    select payment.id as record_id, 'PAYMENT' as entry_type,
                           payment.currency_code,
                           coalesce(payment.completed_at, payment.created_at) as occurred_at,
                           invoice.account_id
                      from billing_payment_attempts payment
                      join billing_invoices invoice on invoice.id = payment.invoice_id
                    union all
                    select credit.id as record_id, 'CREDIT' as entry_type,
                           credit.currency_code, credit.issued_at as occurred_at,
                           invoice.account_id
                      from billing_credits credit
                      join billing_invoices invoice on invoice.id = credit.invoice_id
                    union all
                    select refund.id as record_id, 'REFUND' as entry_type,
                           refund.currency_code,
                           coalesce(refund.completed_at, refund.created_at) as occurred_at,
                           invoice.account_id
                      from billing_refunds refund
                      join billing_payment_attempts payment on payment.id = refund.payment_id
                      join billing_invoices invoice on invoice.id = payment.invoice_id
                   ) timeline
             where timeline.account_id = :accountId
               and (:entryType is null or timeline.entry_type = :entryType)
               and (:currencyCode is null or timeline.currency_code = :currencyCode)
               and (:occurredFrom is null or timeline.occurred_at >= :occurredFrom)
               and (:occurredUntil is null or timeline.occurred_at < :occurredUntil)
            """,
            nativeQuery = true)
    Page<EntryProjection> findByAccount(
            @Param("accountId") UUID accountId,
            @Param("entryType") String entryType,
            @Param("currencyCode") String currencyCode,
            @Param("occurredFrom") Instant occurredFrom,
            @Param("occurredUntil") Instant occurredUntil,
            Pageable pageable);
}
