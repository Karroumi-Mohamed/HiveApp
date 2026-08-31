package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.dto.SubscriptionEntitlementSnapshot;
import com.hiveapp.platform.client.plan.dto.SubscriptionFeatureSnapshot;
import com.hiveapp.shared.quota.QuotaLimitEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionImpactAnalyzerTest {

    @Mock private SubscriptionImpactRegistry impactRegistry;
    @Mock private SubscriptionSnapshotReader snapshotReader;

    @InjectMocks
    private SubscriptionImpactAnalyzer analyzer;

    @Test
    void missingFeatureContributorBlocksFeatureRemoval() {
        UUID accountId = UUID.randomUUID();
        Subscription current = new Subscription();
        SubscriptionEntitlementSnapshot before = snapshot("feature.owned", 10L);
        SubscriptionEntitlementSnapshot after = snapshot(null, null);
        current.setEntitlementSnapshot(before);
        when(snapshotReader.read(before)).thenReturn(Optional.of(before));
        when(impactRegistry.featureUsage(accountId, "feature.owned")).thenReturn(Optional.empty());

        var conflicts = analyzer.analyze(accountId, current, after);

        assertThat(conflicts).singleElement()
                .satisfies(conflict -> assertThat(conflict.code()).isEqualTo("IMPACT_UNKNOWN"));
    }

    @Test
    void missingQuotaMeasurementBlocksOnlyAnActualReduction() {
        UUID accountId = UUID.randomUUID();
        Subscription current = new Subscription();
        SubscriptionEntitlementSnapshot before = snapshot("feature.owned", 10L);
        SubscriptionEntitlementSnapshot reduced = snapshot("feature.owned", 5L);
        current.setEntitlementSnapshot(before);
        when(snapshotReader.read(before)).thenReturn(Optional.of(before));
        when(impactRegistry.quotaUsage(accountId, "feature.owned", "items"))
                .thenReturn(OptionalLong.empty());

        var conflicts = analyzer.analyze(accountId, current, reduced);

        assertThat(conflicts).singleElement()
                .satisfies(conflict -> assertThat(conflict.code()).isEqualTo("QUOTA_USAGE_UNKNOWN"));

        var unchangedConflicts = analyzer.analyze(accountId, current, before);
        assertThat(unchangedConflicts).isEmpty();
        verify(impactRegistry, never()).featureUsage(accountId, "feature.owned");
    }

    private SubscriptionEntitlementSnapshot snapshot(String featureCode, Long quota) {
        List<SubscriptionFeatureSnapshot> features = featureCode == null
                ? List.of()
                : List.of(new SubscriptionFeatureSnapshot(
                        featureCode,
                        quota == null ? List.of() : List.of(new QuotaLimitEntry("items", quota))));
        return new SubscriptionEntitlementSnapshot(
                "PLAN", BigDecimal.ZERO, "USD", BillingCycle.MONTHLY, features, List.of());
    }
}
