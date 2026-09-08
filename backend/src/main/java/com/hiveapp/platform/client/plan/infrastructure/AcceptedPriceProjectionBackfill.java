package com.hiveapp.platform.client.plan.infrastructure;

import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Rebuilds only the lookup projection from accepted evidence, never from today's catalogue. */
@Component
@RequiredArgsConstructor
public class AcceptedPriceProjectionBackfill {
  private final EntityManager entityManager;

  @EventListener(ApplicationReadyEvent.class)
  @Order(6)
  @Transactional
  public void backfill() {
    int offset = 0;
    while (true) {
      var page =
          entityManager
              .createQuery(
                  "select s from Subscription s where s.currentAccountId is not null order by s.id",
                  Subscription.class)
              .setFirstResult(offset)
              .setMaxResults(200)
              .getResultList();
      if (page.isEmpty()) return;
      page.forEach(Subscription::synchronizeHoldingPrices);
      entityManager.flush();
      entityManager.clear();
      offset += page.size();
    }
  }
}
