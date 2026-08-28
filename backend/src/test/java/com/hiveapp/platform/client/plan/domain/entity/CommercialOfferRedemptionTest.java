package com.hiveapp.platform.client.plan.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hiveapp.platform.client.plan.domain.constant.CommercialOfferRedemptionStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CommercialOfferRedemptionTest {

  @Test
  void activeApplicationClaimCannotBeReplaced() {
    Instant now = Instant.parse("2026-08-28T12:00:00Z");
    UUID currentClaim = UUID.randomUUID();
    CommercialOfferRedemption redemption = reserved(currentClaim, now.plusSeconds(30));

    assertThatThrownBy(
            () -> redemption.reclaimApplication(UUID.randomUUID(), now.plusSeconds(60), now))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("An active Offer application claim cannot be replaced.");
    assertThat(redemption.getApplicationClaimId()).isEqualTo(currentClaim);
  }

  @Test
  void expiredApplicationClaimCanBeReplacedBeforeAnOperationExists() {
    Instant now = Instant.parse("2026-08-28T12:00:00Z");
    UUID replacement = UUID.randomUUID();
    CommercialOfferRedemption redemption =
        reserved(UUID.randomUUID(), now.minusSeconds(1));

    redemption.reclaimApplication(replacement, now.plusSeconds(60), now);

    assertThat(redemption.getApplicationClaimId()).isEqualTo(replacement);
    assertThat(redemption.getApplicationLeaseExpiresAt()).isEqualTo(now.plusSeconds(60));
  }

  private CommercialOfferRedemption reserved(UUID claimId, Instant leaseExpiresAt) {
    CommercialOfferRedemption redemption = new CommercialOfferRedemption();
    ReflectionTestUtils.setField(
        redemption, "status", CommercialOfferRedemptionStatus.RESERVED);
    ReflectionTestUtils.setField(redemption, "applicationClaimId", claimId);
    ReflectionTestUtils.setField(
        redemption, "applicationLeaseExpiresAt", leaseExpiresAt);
    return redemption;
  }
}
