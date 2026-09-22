package com.hiveapp.platform.communication;

import static org.assertj.core.api.Assertions.assertThat;

import com.hiveapp.platform.client.plan.dto.*;
import com.hiveapp.shared.quota.QuotaLimitMode;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommunicationSourcesTest {
  @Test
  void reductionsIncludeFiniteAndUnlimitedCapacityNotJustFeatureRemoval() {
    assertThat(CommunicationSources.hasReduction(impact(null, 5L))).isTrue();
    assertThat(CommunicationSources.hasReduction(impact(10L, 5L))).isTrue();
    assertThat(CommunicationSources.hasReduction(impact(5L, 10L))).isFalse();
    assertThat(CommunicationSources.hasReduction(impact(5L, null))).isFalse();
    assertThat(CommunicationSources.hasReduction(impact(5L, 5L))).isFalse();
  }

  private PlanVersionRolloutModels.Impact impact(Long before, Long after) {
    return new PlanVersionRolloutModels.Impact(
        1,
        2,
        null,
        "MAD",
        List.of(),
        List.of(limit(before)),
        List.of(limit(after)),
        List.of(),
        List.of());
  }

  private EffectiveQuotaLimit limit(Long n) {
    return new EffectiveQuotaLimit(
        "platform.staff",
        "members",
        n == null ? QuotaLimitMode.UNLIMITED : QuotaLimitMode.FINITE,
        n,
        0,
        n);
  }
}
