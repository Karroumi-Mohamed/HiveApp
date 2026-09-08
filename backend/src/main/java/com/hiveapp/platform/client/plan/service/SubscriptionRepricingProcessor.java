package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepricingItemRepository;
import com.hiveapp.platform.client.plan.dto.RepricingModels.State;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SubscriptionRepricingProcessor {
  private final SubscriptionRepricingItemRepository items;
  private final SubscriptionRepricingExecutor executor;

  public void processDue(Instant cutoff) {
    // Drain bounded pages before ordinary renewal runs; otherwise the 501st Account
    // could renew at its old price while a reviewed instruction is already due.
    while (true) {
      var due = items.due(State.PENDING, cutoff, PageRequest.of(0, 500));
      if (due.isEmpty()) return;
      for (var id : due) {
        try {
          executor.execute(id, cutoff);
        } catch (RuntimeException failure) {
          executor.recordFailure(id);
        }
      }
    }
  }
}
