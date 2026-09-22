package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.dto.RepricingModels.Delivery;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Durable notice adapters share one transport loop; claims/completion remain short transactions.
 */
public interface CommercialNoticeDeliverySource {
  record Claim(UUID noticeId, UUID claimId, String email, String subject, String body) {}

  List<UUID> due(Instant now, int limit);

  Claim claimNotice(UUID id);

  void completeNotice(Claim claim, Delivery outcome);
}
