package com.hiveapp.platform.communication;

import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** No transport I/O inside an event transaction; a row lock permits multiple bounded workers. */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationWorker {
  private final NotificationEventRepository events;
  private final CommunicationEntryRepository entries;
  private final NotificationCatalog catalog;
  private final TransactionTemplate transactions;
  private final Clock clock;

  @Scheduled(
      fixedDelayString = "${hiveapp.notifications.delay-ms:1000}",
      initialDelayString = "${hiveapp.notifications.delay-ms:1000}")
  public void dispatch() {
    for (var id : events.due(clock.instant(), PageRequest.of(0, 50))) {
      try {
        deliver(id);
      } catch (RuntimeException failure) {
        log.warn("Notification event failed id={} type={}", id, failure.getClass().getSimpleName());
        transactions.executeWithoutResult(
            tx ->
                events
                    .lock(id)
                    .ifPresent(
                        e -> {
                          if (e.getState() != NotificationEvent.State.PENDING) return;
                          int attempts = e.getAttempts() + 1;
                          e.setAttempts(attempts);
                          e.setFailureCode(
                              failure instanceof IllegalArgumentException
                                      || failure instanceof IllegalStateException
                                  ? "CONTRACT_INVALID"
                                  : "DELIVERY_FAILED");
                          e.setNextAttemptAt(
                              clock
                                  .instant()
                                  .plusSeconds(Math.min(3600, 30L << Math.min(attempts, 6))));
                          if (attempts >= 5) e.setState(NotificationEvent.State.FAILED);
                        }));
      }
    }
  }

  public void deliver(UUID id) {
    transactions.executeWithoutResult(
        tx -> {
          var event = events.lock(id).orElseThrow();
          if (event.getState() != NotificationEvent.State.PENDING
              || event.getNextAttemptAt().isAfter(clock.instant())) return;
          var definition = catalog.require(event.getDefinitionKey());
          if (entries.findByEventId(id).isEmpty()) {
            var e = new CommunicationEntry();
            e.setEventId(id);
            e.setSource("EVENT");
            e.setSourceId(id);
            e.setEventType(definition.key());
            e.setTopic(definition.topic());
            e.setKind(definition.kind());
            e.setPriority(definition.priority());
            e.setPurpose(definition.purpose());
            e.setOptional(definition.optional());
            e.setAccountId(event.getAccountId());
            e.setRecipientUserId(event.getRecipientUserId());
            e.setCompanyId(event.getCompanyId());
            e.setAudience(event.getAudience());
            e.setResourceId(event.getResourceId());
            e.setSenderName(event.getSenderName());
            e.setRequiredPermission(
                definition.requiredPermission() == null
                    ? null
                    : definition.requiredPermission().path());
            e.setActionPath(definition.actionPath(event.getResourceId()));
            e.setMessageTitle(event.getMessageTitle());
            e.setMessageBody(event.getMessageBody());
            e.setLanguages(event.getLanguages() == null ? null : event.getLanguages().snapshot());
            e.setAvailableAt(event.getAvailableAt());
            e.setExpiresAt(event.getExpiresAt());
            e.setResolvedAt(event.getResolvedAt());
            e.setCancelled(event.isCancelled());
            e.getDelivery().publish(clock.instant(), event.isEmail());
            entries.saveAndFlush(e);
          }
          event.setState(NotificationEvent.State.DELIVERED);
          event.setFailureCode(null);
        });
  }
}
