package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.entity.CommercialOfferCapacity;
import com.hiveapp.platform.client.plan.dto.CommercialOfferEffectSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionEntitlementSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionFeatureSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionOfferEvaluation;
import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.shared.exception.OfferRedemptionBlockedException;
import com.hiveapp.shared.quota.QuotaLimitEntry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class CommercialOfferContractTest {
    @Test void codeDigestIsNormalizedKeyedAndNeverPlaintext() {
        var hasher = new CommercialOfferCodeHasher(
                new CommercialOfferCodeProperties("test-pepper-that-is-at-least-thirty-two-bytes"));
        String first = hasher.hash("  summer_25 ");
        assertThat(first).isEqualTo(hasher.hash("SUMMER_25")).hasSize(64);
        assertThat(first).doesNotContain("SUMMER");
    }

    @Test void offerQuotaBonusBecomesPartOfImmutableEntitlementSnapshot() {
        var source = new SubscriptionEntitlementSnapshot("PRO", new BigDecimal("25.00"), "USD",
                BillingCycle.MONTHLY, List.of(new SubscriptionFeatureSnapshot("platform.staff",
                List.of(new QuotaLimitEntry("members", 10L)))), List.of());
        var evaluation = evaluation(List.of(
                new CommercialOfferEffectSnapshot.QuotaBonus("platform.staff", "members", 5)));
        var accepted = source.withOfferEvaluation(evaluation);
        assertThat(accepted.basePrice()).isEqualByComparingTo("18.00");
        assertThat(accepted.features().getFirst().quotaConfigs().getFirst().limit()).isEqualTo(15);
        assertThat(accepted.offerEvaluation().offerRevisionId()).isEqualTo(evaluation.offerRevisionId());
    }

    @Test void bonusCannotTargetAResourceOutsideTheAcceptedSelection() {
        var source = SubscriptionEntitlementSnapshot.empty(
                "PRO", new BigDecimal("25.00"), "USD", BillingCycle.MONTHLY);
        assertThatThrownBy(() -> source.withOfferEvaluation(evaluation(List.of(
                new CommercialOfferEffectSnapshot.QuotaBonus("platform.staff", "members", 5)))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test void capacityReservationIsReleasedOrConsumedExactlyOnce() {
        var capacity = CommercialOfferCapacity.create(UUID.randomUUID());
        capacity.reserve(1, 0, 1);
        assertThatThrownBy(() -> capacity.reserve(1, 0, 1))
                .isInstanceOf(OfferRedemptionBlockedException.class);
        capacity.apply();
        assertThat(capacity.getReservedCount()).isZero();
        assertThat(capacity.getAppliedCount()).isOne();
        assertThatThrownBy(capacity::apply).isInstanceOf(IllegalStateException.class);
    }

    private SubscriptionOfferEvaluation evaluation(
            List<CommercialOfferEffectSnapshot.QuotaBonus> bonuses) {
        return new SubscriptionOfferEvaluation(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 2,
                new BigDecimal("25.00"), new BigDecimal("20.00"), new BigDecimal("18.00"),
                new BigDecimal("18.00"), "USD", "OFFER", "Offer is lower", bonuses);
    }
}
