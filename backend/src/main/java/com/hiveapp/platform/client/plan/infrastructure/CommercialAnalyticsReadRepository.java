package com.hiveapp.platform.client.plan.infrastructure;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.BillingOutboxStatus;
import com.hiveapp.platform.client.plan.domain.constant.BillingPaymentStatus;
import com.hiveapp.platform.client.plan.domain.constant.BillingRefundStatus;
import com.hiveapp.platform.client.plan.domain.constant.CommercialAttentionType;
import com.hiveapp.platform.client.plan.domain.constant.CommercialSegmentProductType;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.shared.exception.InvalidRequestException;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Fixed-query read model over authoritative commercial records. It never stores a second truth. */
@Repository
@RequiredArgsConstructor
public class CommercialAnalyticsReadRepository {
    private static final int MAX_FACTS = 100_000;
    private static final String CURRENT_SUBSCRIPTION_STATUSES =
            "('ACTIVE','TRIALING','PAST_DUE','SUSPENDED')";

    private final JdbcTemplate jdbc;

    public List<FinancialFact> financialFacts(
            Instant from,
            Instant until,
            String currencyCode,
            BillingCycle billingCycle
    ) {
        StringBuilder sql = new StringBuilder("""
                select metric, occurred_at, amount, currency_code, billing_cycle
                  from (
                    select 'INVOICED' metric, invoice.issued_at occurred_at,
                           invoice.total_amount amount, invoice.currency_code,
                           invoice.billing_cycle
                      from billing_invoices invoice
                     where invoice.status <> 'CANCELLED'
                    union all
                    select 'COLLECTED' metric, payment.completed_at occurred_at,
                           payment.amount, payment.currency_code, invoice.billing_cycle
                      from billing_payment_attempts payment
                      join billing_invoices invoice on invoice.id = payment.invoice_id
                     where payment.status = 'SUCCEEDED'
                       and payment.trusted_for_settlement = true
                    union all
                    select 'CREDITED' metric, credit.issued_at occurred_at,
                           credit.amount, credit.currency_code, invoice.billing_cycle
                      from billing_credits credit
                      join billing_invoices invoice on invoice.id = credit.invoice_id
                    union all
                    select 'REFUNDED' metric, refund.completed_at occurred_at,
                           refund.amount, refund.currency_code, invoice.billing_cycle
                      from billing_refunds refund
                      join billing_payment_attempts payment on payment.id = refund.payment_id
                      join billing_invoices invoice on invoice.id = payment.invoice_id
                     where refund.status = 'SUCCEEDED'
                  ) fact
                 where fact.occurred_at >= ? and fact.occurred_at < ?
                """);
        List<Object> params = new ArrayList<>(List.of(Timestamp.from(from), Timestamp.from(until)));
        if (currencyCode != null) {
            sql.append(" and fact.currency_code = ?");
            params.add(currencyCode);
        }
        if (billingCycle != null) {
            sql.append(" and fact.billing_cycle = ?");
            params.add(billingCycle.name());
        }
        sql.append(" order by fact.occurred_at, fact.metric limit ").append(MAX_FACTS + 1);
        List<FinancialFact> facts = jdbc.query(sql.toString(), financialFactMapper(), params.toArray());
        requireFactLimit(facts.size());
        return facts;
    }

    public Map<SubscriptionStatus, Long> currentSubscriptionCounts() {
        EnumMap<SubscriptionStatus, Long> result = new EnumMap<>(SubscriptionStatus.class);
        for (SubscriptionStatus status : SubscriptionStatus.values()) result.put(status, 0L);
        jdbc.query("""
                select latest.status, count(*) total
                  from (
                    select status,
                           row_number() over (
                               partition by account_id
                               order by created_at desc, id desc
                           ) sequence
                      from subscriptions
                  ) latest
                 where latest.sequence = 1
                 group by latest.status
                """, (org.springframework.jdbc.core.RowCallbackHandler) rs -> result.put(
                SubscriptionStatus.valueOf(rs.getString("status")), rs.getLong("total")));
        return Map.copyOf(result);
    }

    public List<ConfiguredValue> configuredRecurringValues() {
        return jdbc.query("""
                select current_price_currency_code currency_code,
                       snapshot_billing_cycle billing_cycle,
                       sum(current_price) amount,
                       count(*) subscriptions
                  from subscriptions
                 where status in """ + CURRENT_SUBSCRIPTION_STATUSES + """
                   and current_price is not null
                   and current_price_currency_code is not null
                   and snapshot_billing_cycle is not null
                 group by current_price_currency_code, snapshot_billing_cycle
                 order by current_price_currency_code, snapshot_billing_cycle
                """, (rs, row) -> new ConfiguredValue(
                rs.getString("currency_code"),
                BillingCycle.valueOf(rs.getString("billing_cycle")),
                rs.getBigDecimal("amount"),
                rs.getLong("subscriptions")));
    }

    public FinalityCounts finalityCounts() {
        long pendingPayments = count(
                "select count(*) from billing_payment_attempts where status = ?",
                BillingPaymentStatus.PENDING.name());
        long pendingRefunds = count(
                "select count(*) from billing_refunds where status = ?",
                BillingRefundStatus.PENDING.name());
        long pendingCommands = count(
                "select count(*) from billing_outbox_commands where status in (?, ?)",
                BillingOutboxStatus.PENDING.name(), BillingOutboxStatus.PROCESSING.name());
        return new FinalityCounts(pendingPayments, pendingRefunds, pendingCommands);
    }

    public long operationsNeedingAttention() {
        return count(
                "select count(*) from subscription_change_operations where status = ?",
                SubscriptionChangeStatus.NEEDS_ATTENTION.name());
    }

    public long graceDeadlinesBetween(Instant from, Instant until) {
        return count("""
                select count(*) from subscriptions
                 where status = 'PAST_DUE'
                   and grace_ends_at >= ? and grace_ends_at < ?
                """, Timestamp.from(from), Timestamp.from(until));
    }

    public Map<String, Long> offerOutcomeCounts(Instant from, Instant until) {
        LinkedHashMap<String, Long> result = emptyOfferCounts();
        offerFacts(from, until).forEach(fact -> result.merge(fact.outcome(), 1L, Long::sum));
        return Map.copyOf(result);
    }

    public List<LifecycleFact> lifecycleFacts(Instant from, Instant until) {
        List<LifecycleFact> facts = jdbc.query("""
                select action, before_status, after_status, effective_at
                  from subscription_lifecycle_events
                 where effective_at >= ? and effective_at < ?
                 order by effective_at, id
                """ + " limit " + (MAX_FACTS + 1),
                (rs, row) -> new LifecycleFact(
                        rs.getString("action"),
                        SubscriptionStatus.valueOf(rs.getString("before_status")),
                        SubscriptionStatus.valueOf(rs.getString("after_status")),
                        instant(rs, "effective_at")),
                Timestamp.from(from), Timestamp.from(until));
        requireFactLimit(facts.size());
        return facts;
    }

    public List<OfferFact> offerFacts(Instant from, Instant until) {
        List<OfferFact> facts = jdbc.query("""
                select outcome, occurred_at
                  from (
                    select 'RESERVED' outcome, reserved_at occurred_at
                      from commercial_offer_redemptions
                    union all
                    select status outcome,
                           case when status = 'APPLIED' then applied_at else released_at end occurred_at
                      from commercial_offer_redemptions
                     where status <> 'RESERVED'
                  ) event
                 where event.occurred_at >= ? and event.occurred_at < ?
                 order by event.occurred_at, event.outcome
                """ + " limit " + (MAX_FACTS + 1),
                (rs, row) -> new OfferFact(
                        rs.getString("outcome"), instant(rs, "occurred_at")),
                Timestamp.from(from), Timestamp.from(until));
        requireFactLimit(facts.size());
        return facts;
    }

    public List<ProductHoldingCount> productHoldings() {
        return jdbc.query("""
                select holding.product_type, holding.product_code, count(*) subscriptions
                  from subscription_current_holdings holding
                  join subscriptions subscription on subscription.id = holding.subscription_id
                 where subscription.status in """ + CURRENT_SUBSCRIPTION_STATUSES + """
                 group by holding.product_type, holding.product_code
                 order by holding.product_type, count(*) desc, holding.product_code
                """, (rs, row) -> new ProductHoldingCount(
                CommercialSegmentProductType.valueOf(rs.getString("product_type")),
                rs.getString("product_code"), rs.getLong("subscriptions")));
    }

    public Page<AttentionFact> attention(CommercialAttentionType type, Pageable pageable) {
        String union = attentionUnion();
        String filter = type == null ? "" : " where attention_type = ?";
        List<Object> params = new ArrayList<>();
        if (type != null) params.add(type.name());
        long total = jdbc.queryForObject(
                "select count(*) from (" + union + ") attention" + filter,
                Long.class, params.toArray());
        params.add(pageable.getPageSize());
        params.add(pageable.getOffset());
        List<AttentionFact> content = jdbc.query(
                "select * from (" + union + ") attention" + filter
                        + " order by occurred_at desc, record_id desc limit ? offset ?",
                (rs, row) -> new AttentionFact(
                        CommercialAttentionType.valueOf(rs.getString("attention_type")),
                        uuid(rs.getObject("record_id")),
                        uuid(rs.getObject("account_id")),
                        rs.getString("account_name"),
                        rs.getString("record_status"),
                        instant(rs, "occurred_at"),
                        nullableInstant(rs, "due_at"),
                        rs.getBigDecimal("amount"),
                        rs.getString("currency_code"),
                        rs.getString("reason")),
                params.toArray());
        return new PageImpl<>(content, pageable, total);
    }

    private String attentionUnion() {
        return """
                select 'PAST_DUE' attention_type, subscription.id record_id,
                       account.id account_id, account.name account_name,
                       subscription.status record_status,
                       coalesce(subscription.past_due_at, subscription.created_at) occurred_at,
                       subscription.grace_ends_at due_at,
                       cast(null as decimal(19,4)) amount,
                       cast(null as varchar(3)) currency_code,
                       cast(null as varchar(2000)) reason
                  from subscriptions subscription
                  join accounts account on account.id = subscription.account_id
                 where subscription.status = 'PAST_DUE'
                union all
                select 'SUSPENDED', subscription.id, account.id, account.name,
                       subscription.status,
                       coalesce(subscription.suspended_at, subscription.created_at),
                       subscription.current_period_end,
                       cast(null as decimal(19,4)), cast(null as varchar(3)),
                       subscription.suspension_reason
                  from subscriptions subscription
                  join accounts account on account.id = subscription.account_id
                 where subscription.status = 'SUSPENDED'
                union all
                select 'OPEN_INVOICE', invoice.id, account.id, account.name,
                       invoice.status, invoice.issued_at, invoice.period_end,
                       invoice.total_amount, invoice.currency_code,
                       cast(null as varchar(2000))
                  from billing_invoices invoice
                  join accounts account on account.id = invoice.account_id
                 where invoice.status = 'OPEN'
                union all
                select 'CHANGE_NEEDS_ATTENTION', operation.id, account.id, account.name,
                       operation.status, operation.updated_at, operation.effective_at,
                       cast(null as decimal(19,4)), cast(null as varchar(3)),
                       operation.attention_reason
                  from subscription_change_operations operation
                  join accounts account on account.id = operation.account_id
                 where operation.status = 'NEEDS_ATTENTION'
                """;
    }

    private long count(String sql, Object... params) {
        Long value = jdbc.queryForObject(sql, Long.class, params);
        return value == null ? 0L : value;
    }

    private void requireFactLimit(int size) {
        if (size > MAX_FACTS) {
            throw new InvalidRequestException(
                    "The analytics range contains too many facts; choose a narrower range or filters.");
        }
    }

    private LinkedHashMap<String, Long> emptyOfferCounts() {
        LinkedHashMap<String, Long> counts = new LinkedHashMap<>();
        counts.put("RESERVED", 0L);
        counts.put("APPLIED", 0L);
        counts.put("CANCELLED", 0L);
        counts.put("FAILED", 0L);
        return counts;
    }

    private org.springframework.jdbc.core.RowMapper<FinancialFact> financialFactMapper() {
        return (rs, row) -> new FinancialFact(
                rs.getString("metric"),
                instant(rs, "occurred_at"),
                rs.getBigDecimal("amount"),
                rs.getString("currency_code"),
                BillingCycle.valueOf(rs.getString("billing_cycle")));
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Instant value = nullableInstant(rs, column);
        if (value == null) throw new IllegalStateException(column + " is required");
        return value;
    }

    private Instant nullableInstant(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return switch (value) {
            case null -> null;
            case Instant instant -> instant;
            case OffsetDateTime offset -> offset.toInstant();
            case Timestamp timestamp -> timestamp.toInstant();
            case java.util.Date date -> date.toInstant();
            default -> Instant.parse(value.toString());
        };
    }

    private UUID uuid(Object value) {
        return value instanceof UUID id ? id : UUID.fromString(value.toString());
    }

    public record FinancialFact(
            String metric,
            Instant occurredAt,
            BigDecimal amount,
            String currencyCode,
            BillingCycle billingCycle
    ) {}

    public record ConfiguredValue(
            String currencyCode,
            BillingCycle billingCycle,
            BigDecimal amount,
            long subscriptions
    ) {}

    public record FinalityCounts(
            long pendingPayments,
            long pendingRefunds,
            long pendingProviderCommands
    ) {}

    public record LifecycleFact(
            String action,
            SubscriptionStatus beforeStatus,
            SubscriptionStatus afterStatus,
            Instant occurredAt
    ) {}

    public record OfferFact(String outcome, Instant occurredAt) {}

    public record ProductHoldingCount(
            CommercialSegmentProductType productType,
            String productCode,
            long subscriptions
    ) {}

    public record AttentionFact(
            CommercialAttentionType type,
            UUID recordId,
            UUID accountId,
            String accountName,
            String status,
            Instant occurredAt,
            Instant dueAt,
            BigDecimal amount,
            String currencyCode,
            String reason
    ) {}
}
