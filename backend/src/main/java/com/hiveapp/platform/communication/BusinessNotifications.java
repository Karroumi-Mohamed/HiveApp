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
    if (c.getStatus() != CollaborationStatus.PENDING)
      publisher.resolve(CoreNotification.B2B_REQUEST, c.getId());
    String title =
        c.getStatus() == CollaborationStatus.PENDING
            ? "Demande de collaboration"
            : "Collaboration mise à jour";
    String state =
        switch (c.getStatus()) {
          case PENDING -> "en attente";
          case ACTIVE -> "active";
          case SUSPENDED -> "suspendue";
          case CANCELLED -> "annulée";
          case REJECTED -> "refusée";
          case REVOKED -> "révoquée";
        };
    String body = "État de la collaboration : " + state + ". Consultez les détails autorisés.";
    String occurrence = c.getId() + ":" + c.getVersion() + ":" + c.getStatus();
    for (var account :
        java.util.List.of(c.getClientAccount().getId(), c.getProviderAccount().getId()))
      publisher.publish(
          type,
          occurrence,
          NotificationPublisher.Target.account(account),
          c.getId(),
          title,
          body,
          false,
          null,
          null);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void memberCreated(Member member) {
    publisher.publish(
        CoreNotification.MEMBER_CREATED,
        member.getId().toString(),
        NotificationPublisher.Target.account(member.getAccount().getId()),
        member.getId(),
        "Nouveau membre",
        "Un membre a été ajouté à votre compte. Consultez la liste des membres pour les détails"
            + " autorisés.",
        false,
        null,
        null);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void memberChanged(Member member) {
    // One occurrence per committed lifecycle operation, not one immutable identity per member.
    publisher.publish(
        CoreNotification.MEMBER_ACCESS_CHANGED,
        UUID.randomUUID().toString(),
        NotificationPublisher.Target.account(member.getAccount().getId()),
        member.getId(),
        "Accès d’un membre modifié",
        "L’accès d’un membre de votre compte a été modifié. Consultez les détails autorisés.",
        false,
        null,
        null);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void payment(BillingInvoice invoice, UUID attemptId, boolean settled) {
    var type = settled ? CoreNotification.PAYMENT_RECEIVED : CoreNotification.PAYMENT_FAILED;
    if (settled) {
      publisher.resolve(CoreNotification.PAYMENT_FAILED, invoice.getId());
      publisher.resolve(CoreNotification.BILLING_ATTENTION, invoice.getId());
    }
    publisher.publish(
        type,
        attemptId + ":" + settled,
        NotificationPublisher.Target.account(invoice.getAccount().getId()),
        invoice.getId(),
        settled ? "Paiement enregistré" : "Paiement non abouti",
        settled
            ? "Le règlement de votre facture a été confirmé. Consultez le document pour les"
                + " montants et conditions."
            : "Le paiement de votre facture n’a pas abouti. Consultez la facturation avant de"
                + " réessayer.",
        true,
        null,
        null);
    if (!settled)
      publisher.publish(
          CoreNotification.BILLING_ATTENTION,
          attemptId.toString(),
          NotificationPublisher.Target.platform(),
          invoice.getId(),
          "Paiement à vérifier",
          "Une tentative de paiement a échoué. Consultez la facture et ses opérations autorisées.",
          false,
          null,
          null);
  }
}
