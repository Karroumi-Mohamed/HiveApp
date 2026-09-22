package com.hiveapp.platform.communication;

import com.hiveapp.platform.client.plan.domain.repository.*;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Bounded migration on a worker, never on an inbox GET. Each source row serializes its own index.
 */
@Component
@RequiredArgsConstructor
public class CommunicationBackfill {
  private final EntityManager em;
  private final TransactionTemplate transactions;
  private final PlanContentNoticeRepository content;
  private final SubscriptionRepricingItemRepository pricing;
  private final CommunicationSources sources;

  @Scheduled(
      fixedDelayString = "${hiveapp.communications.backfill-delay-ms:30000}",
      initialDelayString = "${hiveapp.communications.backfill-delay-ms:30000}")
  public void backfill() {
    var contentIds =
        em.createQuery(
                "select n.id from PlanContentNotice n where not exists (select e.id from"
                    + " CommunicationEntry e where e.source='PLAN_CONTENT' and e.sourceId=n.id)"
                    + " order by n.id",
                UUID.class)
            .setMaxResults(100)
            .getResultList();
    for (var id : contentIds)
      transactions.executeWithoutResult(status -> content.lock(id).ifPresent(sources::index));
    var priceIds =
        em.createQuery(
                "select n.id from SubscriptionRepricingItem n where n.noticeCreatedAt is not null"
                    + " and not exists (select e.id from CommunicationEntry e where"
                    + " e.source='REPRICING' and e.sourceId=n.id) order by n.id",
                UUID.class)
            .setMaxResults(100)
            .getResultList();
    for (var id : priceIds)
      transactions.executeWithoutResult(status -> pricing.lock(id).ifPresent(sources::index));
  }
}
