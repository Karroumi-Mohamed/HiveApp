package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeJobItemStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeJobStatus;
import com.hiveapp.platform.client.plan.domain.entity.SubscriptionChangeJob;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionChangeJobItemRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionChangeJobRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Small locked transitions used by the scheduler and independently testable from item execution. */
@Service
@RequiredArgsConstructor
public class SubscriptionChangeJobTransitionService {
  private final SubscriptionChangeJobRepository jobs;
  private final SubscriptionChangeJobItemRepository items;

  @Transactional
  public boolean claimOrResume(UUID jobId, Instant now) {
    SubscriptionChangeJob job = jobs.lockById(jobId).orElse(null);
    if (job == null) return false;
    if (job.getStatus() == SubscriptionChangeJobStatus.RUNNING) return true;
    if ((job.getStatus() != SubscriptionChangeJobStatus.QUEUED
            && job.getStatus() != SubscriptionChangeJobStatus.SCHEDULED)
        || job.getExecuteAt().isAfter(now)) {
      return false;
    }
    job.start(now);
    jobs.saveAndFlush(job);
    return true;
  }

  @Transactional(readOnly = true)
  public List<UUID> readyItemIds(UUID jobId) {
    return items.findIdsByJobAndStatuses(jobId, List.of(SubscriptionChangeJobItemStatus.READY));
  }

  @Transactional
  public boolean completeIfTerminal(UUID jobId, Instant now) {
    SubscriptionChangeJob job = jobs.lockById(jobId).orElse(null);
    if (job == null || job.getStatus() != SubscriptionChangeJobStatus.RUNNING) return false;
    if (!job.completeIfTerminal(now)) return false;
    jobs.saveAndFlush(job);
    return true;
  }
}
