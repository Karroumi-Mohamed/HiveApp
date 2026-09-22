package com.hiveapp.platform.communication;

import com.hiveapp.platform.client.collaboration.domain.constant.CollaborationStatus;
import com.hiveapp.platform.client.collaboration.domain.entity.Collaboration;
import com.hiveapp.platform.client.member.domain.entity.Member;
import com.hiveapp.platform.client.plan.domain.entity.BillingInvoice;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/**
 * Minimal event-specific summaries; private reasons, credentials and provider errors never leak.
 */
@Service
@RequiredArgsConstructor
public class BusinessNotifications {
  private final NotificationPublisher publisher;

  @Transactional(propagation = Propagation.MANDATORY)
  public void collaboration(Collaboration c) {
    var type =
        c.getStatus() == CollaborationStatus.PENDING
            ? CoreNotification.B2B_REQUEST
            : CoreNotification.B2B_CHANGED;
    if (c.getStatus() != CollaborationStatus.PENDING) {
      publisher.resolve(CoreNotification.B2B_REQUEST, c.getId());
      publisher.resolve(CoreNotification.B2B_REQUEST_SENT, c.getId());
    }
    String occurrence = c.getId() + ":" + c.getVersion() + ":" + c.getStatus();
    for (var account :
        java.util.List.of(c.getClientAccount().getId(), c.getProviderAccount().getId()))
      publish(
          c.getStatus() == CollaborationStatus.PENDING
                  && account.equals(c.getClientAccount().getId())
              ? CoreNotification.B2B_REQUEST_SENT
              : type,
          occurrence,
          NotificationPublisher.Target.account(account),
          c.getId(),
          false);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void memberCreated(Member member) {
    publish(
        CoreNotification.MEMBER_CREATED,
        member.getId().toString(),
        NotificationPublisher.Target.account(member.getAccount().getId()),
        member.getId(),
        false);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void memberChanged(Member member) {
    // One occurrence per committed lifecycle operation, not one immutable identity per member.
    publish(
        CoreNotification.MEMBER_ACCESS_CHANGED,
        UUID.randomUUID().toString(),
        NotificationPublisher.Target.account(member.getAccount().getId()),
        member.getId(),
        false);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void payment(BillingInvoice invoice, UUID attemptId, boolean settled) {
    var type = settled ? CoreNotification.PAYMENT_RECEIVED : CoreNotification.PAYMENT_FAILED;
    if (settled) {
      publisher.resolve(CoreNotification.PAYMENT_FAILED, invoice.getId());
      publisher.resolve(CoreNotification.BILLING_ATTENTION, invoice.getId());
    }
    publish(
        type,
        attemptId + ":" + settled,
        NotificationPublisher.Target.account(invoice.getAccount().getId()),
        invoice.getId(),
        true);
    if (!settled)
      publish(
          CoreNotification.BILLING_ATTENTION,
          attemptId.toString(),
          NotificationPublisher.Target.platform(),
          invoice.getId(),
          false);
  }

  private void publish(
      CoreNotification type,
      String occurrence,
      NotificationPublisher.Target target,
      UUID resource,
      boolean email) {
    var content = java.util.Objects.requireNonNull(type.content(java.util.Locale.FRENCH));
    publisher.publish(
        type, occurrence, target, resource, content.title(), content.body(), email, null, null);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void paymentCancelled(BillingInvoice invoice) {
    publisher.withdraw(CoreNotification.PAYMENT_FAILED, invoice.getId());
    publisher.withdraw(CoreNotification.BILLING_ATTENTION, invoice.getId());
  }
}
