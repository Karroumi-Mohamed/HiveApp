package com.hiveapp.platform.communication;

import static com.hiveapp.platform.communication.CommunicationModels.*;

import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.member.domain.repository.MemberRepository;
import com.hiveapp.shared.exception.InvalidRequestException;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InternalNotificationService {
  private final NotificationAccess access;
  private final MemberRepository members;
  private final AccountRepository accounts;
  private final NotificationPublisher publisher;
  private final jakarta.persistence.EntityManager entities;
  private final com.fasterxml.jackson.databind.ObjectMapper json;
  private final java.time.Clock clock;

  @Transactional(readOnly = true)
  public Page<MemberChoice> recipients(String search, Pageable page) {
    var v = access.viewer(false);
    String q =
        search == null
            ? ""
            : search
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    return members
        .findAll(
            (r, c, b) ->
                b.and(
                    b.equal(r.get("account").get("id"), v.accountId()),
                    b.isTrue(r.get("isActive")),
                    b.isTrue(r.get("user").get("isActive")),
                    b.like(b.lower(r.get("displayName")), "%" + q + "%", '!')),
            page)
        .map(
            m ->
                new MemberChoice(
                    m.getId(), m.getDisplayName() == null ? "Membre" : m.getDisplayName()));
  }

  @Transactional
  public void send(InternalNotice request) {
    var v = access.viewer(false);
    accounts.findByIdForSubscriptionUpdate(v.accountId()).orElseThrow();
    if (new HashSet<>(request.memberIds()).size() != request.memberIds().size())
      throw new InvalidRequestException("Recipients must be unique.");
    String key = v.accountId() + ":" + v.userId() + ":" + request.commandId();
    String fingerprint;
    try {
      var payload =
          List.of(
              request.messageTitle().trim(),
              request.messageBody().trim(),
              request.memberIds().stream().sorted().toList());
      fingerprint =
          HexFormat.of()
              .formatHex(
                  java.security.MessageDigest.getInstance("SHA-256")
                      .digest(json.writeValueAsBytes(payload)));
    } catch (java.security.NoSuchAlgorithmException
        | com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
    var prior = entities.find(NotificationSendCommand.class, key);
    if (prior != null) {
      if (!prior.getPayloadHash().equals(fingerprint))
        throw new InvalidRequestException(
            "This send command already belongs to a different message or audience.");
      return;
    }
    var selected = members.findAllById(request.memberIds());
    if (selected.size() != request.memberIds().size()
        || selected.stream()
            .anyMatch(
                m ->
                    !m.getAccount().getId().equals(v.accountId())
                        || !m.isActive()
                        || !m.getUser().isActive()))
      throw new InvalidRequestException("Choose active members of your own Account.");
    entities.persist(new NotificationSendCommand(key, fingerprint, clock.instant()));
    for (var member : selected)
      publisher.publish(
          CoreNotification.INTERNAL_INFORMATION,
          v.userId() + ":" + request.commandId(),
          NotificationPublisher.Target.member(v.accountId(), member.getUser().getId()),
          null,
          request.messageTitle(),
          request.messageBody(),
          false,
          null,
          null);
  }
}
