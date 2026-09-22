package com.hiveapp.platform.client.plan.service;

import static org.assertj.core.api.Assertions.*;

import com.hiveapp.platform.client.plan.domain.entity.CommercialNoticeDeliveryState;
import com.hiveapp.platform.client.plan.dto.RepricingModels.Delivery;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CommercialNoticeDeliveryStateTest {
  @Test
  void abandonedLeaseRecoversAndStaleCompletionCannotOverwriteCurrentAttempt() {
    var state = new CommercialNoticeDeliveryState();
    var now = Instant.parse("2026-01-01T00:00:00Z");
    UUID recipient = UUID.randomUUID();
    state.publish(now, true);
    var first = state.claim(recipient, now);
    assertThat(state.claimable(now.plusSeconds(299))).isFalse();
    var second = state.claim(recipient, now.plusSeconds(301));
    state.complete(first, Delivery.SENT);
    assertThat(state.getDelivery()).isEqualTo(Delivery.SENDING);
    state.complete(second, Delivery.FAILED);
    assertThat(state.retry()).isTrue();
    assertThat(state.getAttempts()).isEqualTo(2);
    var third = state.claim(recipient, now.plusSeconds(302));
    assertThat(state.automaticAttemptsExhausted()).isTrue();
    state.complete(third, Delivery.SENT);
    assertThat(state.retryForNewRecipient(recipient)).isFalse();
    assertThat(state.retryForNewRecipient(UUID.randomUUID())).isTrue();
  }
}
