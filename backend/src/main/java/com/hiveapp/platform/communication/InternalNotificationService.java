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
  private final NotificationSendCommandRepository commands;
  private final NotificationEventRepository events;

  @Transactional(readOnly = true)
  public Page<SentNotice> sent(Pageable page) {
    var viewer = access.viewer(false);
    var found = commands.findByAccountIdAndSenderUserId(viewer.accountId(), viewer.userId(),
        PageRequest.of(page.getPageNumber(), page.getPageSize(), Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("commandKey"))));
    var counts = new HashMap<UUID, EnumMap<NotificationEvent.State, Long>>();
    if (!found.isEmpty())
      for (var row : events.sentCounts(viewer.accountId(), viewer.userId(), found.map(NotificationSendCommand::getCommandId).getContent()))
        counts.computeIfAbsent((UUID) row[0], ignored -> new EnumMap<>(NotificationEvent.State.class))
            .put((NotificationEvent.State) row[1], ((Number) row[2]).longValue());
    return found.map(command -> {
      var result = counts.getOrDefault(command.getCommandId(), new EnumMap<>(NotificationEvent.State.class));
      return new SentNotice(command.getCommandId(), command.getMessageTitle(), command.getMessageBody(), command.getCreatedAt(),
          command.getRecipients(), result.getOrDefault(NotificationEvent.State.DELIVERED, 0L),
          result.getOrDefault(NotificationEvent.State.FAILED, 0L), result.getOrDefault(NotificationEvent.State.PENDING, 0L));
    });
  }

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
    entities.persist(new NotificationSendCommand(key, fingerprint, clock.instant(), v.accountId(), v.userId(), request));
    var sender = members.findByAccountIdAndUserId(v.accountId(), v.userId()).orElseThrow();
    for (var member : selected) {
      UUID id = publisher.publish(
          CoreNotification.INTERNAL_INFORMATION,
          v.userId() + ":" + request.commandId(),
          NotificationPublisher.Target.member(v.accountId(), member.getUser().getId()),
          request.commandId(),
          request.messageTitle(),
          request.messageBody(),
          false,
          null,
          null);
      var event = entities.find(NotificationEvent.class, id);
      event.setSenderUserId(v.userId());
      event.setSenderName(sender.getDisplayName() == null ? "Membre" : sender.getDisplayName());
    }
  }
}
