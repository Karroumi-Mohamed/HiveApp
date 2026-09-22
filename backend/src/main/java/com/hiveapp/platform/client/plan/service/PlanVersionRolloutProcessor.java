package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.*;
import com.hiveapp.platform.client.plan.domain.repository.*;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/** Invoked by the existing subscription job scheduler, with bounded work per pass. */
@Component
@RequiredArgsConstructor
@Slf4j
public class PlanVersionRolloutProcessor {
  public static final int ITEM_BATCH_SIZE = 50;
  private final SubscriptionChangeJobRepository jobs;
  private final SubscriptionChangeJobItemRepository items;
  private final SubscriptionChangeJobTransitionService transitions;
  private final PlanVersionRolloutWorker worker;

  public boolean processIfContent(UUID jobId, Instant cutoff) {
    var job = jobs.findById(jobId).orElse(null);
    if (job == null || !job.isContentVersion()) return false;
    boolean assessing = job.getStatus() == SubscriptionChangeJobStatus.ASSESSING;
    if (!assessing && !transitions.claimOrResume(jobId, cutoff)) return true;
    var statuses =
        assessing
            ? List.of(SubscriptionChangeJobItemStatus.ASSESSING)
            : List.of(
                SubscriptionChangeJobItemStatus.READY, SubscriptionChangeJobItemStatus.WAITING);
    for (UUID itemId :
        items.findDueItemIds(jobId, statuses, cutoff, PageRequest.of(0, ITEM_BATCH_SIZE))) {
      try {
        if (assessing) worker.assess(jobId, itemId);
        else worker.execute(jobId, itemId);
      } catch (RuntimeException failure) {
        log.warn(
            "Plan version rollout item failed job={} item={} type={}",
            jobId,
            itemId,
            failure.getClass().getSimpleName());
        worker.failed(jobId, itemId, failure);
      }
    }
    if (assessing) worker.finishAssessment(jobId);
    else worker.finishExecution(jobId);
    return true;
  }
}
