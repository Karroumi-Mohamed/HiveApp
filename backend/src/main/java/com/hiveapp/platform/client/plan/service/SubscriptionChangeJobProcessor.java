package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeJobStatus;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionChangeJobRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Durable, resumable scheduler for reviewed population changes. */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionChangeJobProcessor {
  private static final int JOB_BATCH_SIZE = 20;

  private final SubscriptionChangeJobRepository jobs;
  private final SubscriptionChangeJobTransitionService transitions;
  private final SubscriptionChangeJobItemExecutor executor;
  private final PlanVersionRolloutProcessor contentRollouts;
  private final Clock clock;

  @Scheduled(fixedDelayString = "${hiveapp.subscriptions.change-job-delay-ms:1000}")
  public void processDue() {
    processDue(clock.instant());
  }

  public void processDue(Instant cutoff) {
    List<UUID> due =
        jobs.findDueIds(
            List.of(
                SubscriptionChangeJobStatus.ASSESSING,
                SubscriptionChangeJobStatus.QUEUED,
                SubscriptionChangeJobStatus.SCHEDULED,
                SubscriptionChangeJobStatus.RUNNING),
            cutoff,
            PageRequest.of(0, JOB_BATCH_SIZE));
    for (UUID jobId : due) {
      try {
        processOne(jobId, cutoff);
      } catch (RuntimeException failure) {
        log.error("Failed to process subscription change job {}", jobId, failure);
      }
    }
  }

  void processOne(UUID jobId, Instant cutoff) {
    if (contentRollouts.processIfContent(jobId, cutoff)) return;
    if (!transitions.claimOrResume(jobId, cutoff)) return;
    for (UUID itemId : transitions.readyItemIds(jobId)) {
      try {
        executor.execute(itemId);
      } catch (RuntimeException failure) {
        try {
          executor.recordFailure(itemId, failure);
        } catch (RuntimeException recordingFailure) {
          failure.addSuppressed(recordingFailure);
          log.error("Failed to record subscription job item failure {}", itemId, failure);
        }
      }
    }
    transitions.completeIfTerminal(jobId, clock.instant());
  }
}
