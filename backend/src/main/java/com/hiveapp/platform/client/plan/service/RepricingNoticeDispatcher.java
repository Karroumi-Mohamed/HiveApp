package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.dto.RepricingModels.Delivery;
import com.hiveapp.shared.email.*;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class RepricingNoticeDispatcher {
  private final List<CommercialNoticeDeliverySource> sources;
  private final EmailService email;
  private final Clock clock;

  @Scheduled(fixedDelayString = "${hiveapp.subscriptions.notice-delay-ms:30000}")
  @org.springframework.transaction.annotation.Transactional(
      propagation = org.springframework.transaction.annotation.Propagation.NEVER)
  public void dispatch() {
    for (var source : sources) {
      try {
        for (var id : source.due(clock.instant(), 20)) {
          try {
            var claim = source.claimNotice(id);
            if (claim == null) continue;
            Delivery outcome;
            try {
              // Financial details stay behind current Account authorization in the portal.
              var result = email.sendCommercialNotice(claim.email(), claim.subject(), claim.body());
              outcome = result == EmailDispatchOutcome.SENT ? Delivery.SENT : Delivery.SUPPRESSED;
            } catch (RuntimeException failure) {
              outcome = Delivery.FAILED;
            }
            source.completeNotice(claim, outcome);
          } catch (RuntimeException failure) {
            log.warn(
                "Commercial notice dispatch failed source={} notice={} type={}",
                source.getClass().getSimpleName(),
                id,
                failure.getClass().getSimpleName());
          }
        }
      } catch (RuntimeException failure) {
        log.warn(
            "Commercial notice source unavailable source={} type={}",
            source.getClass().getSimpleName(),
            failure.getClass().getSimpleName());
      }
    }
  }
}
