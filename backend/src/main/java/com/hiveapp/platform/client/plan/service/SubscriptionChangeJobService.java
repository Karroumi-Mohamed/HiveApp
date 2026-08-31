package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeJobItemStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeJobStatus;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeJobModels;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SubscriptionChangeJobService {
  SubscriptionChangeJobModels.Preview preview(
      UUID actorUserId, SubscriptionChangeJobModels.PreviewRequest request);

  SubscriptionChangeJobModels.Detail confirm(
      UUID jobId, UUID actorUserId, SubscriptionChangeJobModels.ConfirmRequest request);

  Page<SubscriptionChangeJobModels.Summary> list(
      SubscriptionChangeJobStatus status, Pageable pageable);

  SubscriptionChangeJobModels.Detail get(UUID jobId);

  Page<SubscriptionChangeJobModels.Item> results(
      UUID jobId, SubscriptionChangeJobItemStatus status, Pageable pageable);

  List<SubscriptionChangeJobModels.Identity> resolveIdentities(
      UUID jobId, Collection<UUID> itemIds);

  SubscriptionChangeJobModels.Detail cancel(
      UUID jobId, UUID actorUserId, SubscriptionChangeJobModels.CancelRequest request);

  SubscriptionChangeJobModels.Detail retry(
      UUID jobId, UUID actorUserId, SubscriptionChangeJobModels.RetryRequest request);
}
