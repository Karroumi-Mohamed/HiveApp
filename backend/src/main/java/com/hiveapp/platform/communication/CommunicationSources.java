package com.hiveapp.platform.communication;

import static com.hiveapp.platform.communication.CommunicationModels.*;

import com.hiveapp.platform.client.plan.domain.entity.*;
import com.hiveapp.platform.client.plan.domain.repository.*;
import com.hiveapp.platform.generated.PlatformPermissions;
import dev.karroumi.permissionizer.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Source adapters index only safe client summaries. Original records remain authoritative. */
@Service
@RequiredArgsConstructor
public class CommunicationSources {
  private final CommunicationEntryRepository entries;
  private final PlanContentNoticeRepository content;
  private final SubscriptionRepricingItemRepository pricing;
  private final NotificationCatalog catalog;
  private final org.springframework.beans.factory.ObjectProvider<NotificationOfferEligibility>
      offers;

  public List<String> allowedPermissions() {
    List<Permission> permissions = new ArrayList<>();
    permissions.add(PlatformPermissions.Subscription.Read_content_notices.permission());
    permissions.add(PlatformPermissions.Subscription.Read_price_notices.permission());
    for (var definition : catalog.all()) {
      if (definition.requiredPermission() != null) permissions.add(definition.requiredPermission());
    }
    return permissions.stream()
        .distinct()
        .filter(PermissionGuard::has)
        .map(Permission::path)
        .toList();
  }

  @Transactional
  public void index(PlanContentNotice notice) {
    if (entries
        .findBySourceAndSourceIdAndAccountId(
            "PLAN_CONTENT", notice.getId(), notice.getAccount().getId())
        .isPresent()) return;
    var e = base("PLAN_CONTENT", notice.getId(), notice.getAccount().getId());
    e.setKind(hasReduction(notice.getImpact()) ? Kind.WARNING : Kind.NOTICE);
    e.setPriority(e.getKind() == Kind.WARNING ? Priority.HIGH : Priority.NORMAL);
    e.setTopic(Topic.COMMERCIAL);
    e.setMessageTitle(notice.getPlanName());
    e.setMessageBody("V" + notice.getSourceVersion() + " → V" + notice.getTargetVersion());
    e.setRequiredPermission(PlatformPermissions.Subscription.Read_content_notices.path());
    e.setActionPath("/app/subscription?tab=notices");
    e.setAvailableAt(notice.getDelivery().getCreatedAt());
    e.getDelivery().publish(e.getAvailableAt(), false);
    entries.save(e);
  }

  @Transactional
  public void index(SubscriptionRepricingItem notice) {
    if (notice.getNoticeCreatedAt() == null
        || entries
            .findBySourceAndSourceIdAndAccountId(
                "REPRICING", notice.getId(), notice.getAccount().getId())
            .isPresent()) return;
    var e = base("REPRICING", notice.getId(), notice.getAccount().getId());
    e.setKind(Kind.WARNING);
    e.setPriority(Priority.HIGH);
    e.setTopic(Topic.BILLING);
    e.setMessageTitle("Tarif de votre abonnement");
    e.setMessageBody("Un changement de tarif a été préparé. Consultez son état et ses conditions.");
    e.setRequiredPermission(PlatformPermissions.Subscription.Read_price_notices.path());
    e.setActionPath("/app/subscription?tab=notices");
    e.setAvailableAt(notice.getNoticeCreatedAt());
    e.getDelivery().publish(e.getAvailableAt(), false);
    entries.save(e);
  }

  private CommunicationEntry base(String source, UUID id, UUID account) {
    var e = new CommunicationEntry();
    e.setSource(source);
    e.setSourceId(id);
    e.setAccountId(account);
    e.setPurpose(Purpose.SERVICE);
    return e;
  }

  public Map<UUID, String> states(List<CommunicationEntry> found) {
    var result = new HashMap<UUID, String>();
    var contentIds =
        found.stream()
            .filter(e -> e.getSource().equals("PLAN_CONTENT"))
            .map(CommunicationEntry::getSourceId)
            .toList();
    var priceIds =
        found.stream()
            .filter(e -> e.getSource().equals("REPRICING"))
            .map(CommunicationEntry::getSourceId)
            .toList();
    var values = new HashMap<UUID, String>();
    content.findAllById(contentIds).forEach(n -> values.put(n.getId(), n.getState().name()));
    pricing.findAllById(priceIds).forEach(n -> values.put(n.getId(), n.getStatus().name()));
    found.stream()
        .filter(e -> Set.of("PLAN_CONTENT", "REPRICING").contains(e.getSource()))
        .forEach(e -> result.put(e.getId(), values.getOrDefault(e.getSourceId(), "WITHDRAWN")));
    // At most one live eligibility check per distinct account/Offer in this bounded page.
    var offerStates = new HashMap<String, String>();
    for (var entry : found) {
      if (entry.getKind() != Kind.OFFER) continue;
      String key = entry.getAccountId() + ":" + entry.getResourceId();
      String state =
          offerStates.computeIfAbsent(
              key,
              ignored -> {
                if (entry.getAccountId() == null || entry.getResourceId() == null)
                  return "UNAVAILABLE";
                try {
                  offers.getObject().requireAvailable(entry.getAccountId(), entry.getResourceId());
                  return "PUBLISHED";
                } catch (com.hiveapp.shared.exception.OfferNotAvailableException unavailable) {
                  return "UNAVAILABLE";
                }
              });
      result.put(entry.getId(), state);
    }
    return result;
  }

  public boolean isInactiveState(String state) {
    return "CANCELLED".equals(state)
        || "WITHDRAWN".equals(state)
        || "APPLIED".equals(state)
        || "UNAVAILABLE".equals(state);
  }

  public boolean inactive(CommunicationEntry e) {
    return e.getResolvedAt() != null || isInactiveState(states(List.of(e)).get(e.getId()));
  }

  public static boolean hasReduction(
      com.hiveapp.platform.client.plan.dto.PlanVersionRolloutModels.Impact impact) {
    if (!impact.removedFeatures().isEmpty()) return true;
    return impact.afterLimits().stream()
        .anyMatch(
            after ->
                after.effectiveLimit() != null
                    && impact.beforeLimits().stream()
                        .anyMatch(
                            before ->
                                before.featureCode().equals(after.featureCode())
                                    && before.resource().equals(after.resource())
                                    && (before.effectiveLimit() == null
                                        || before.effectiveLimit() > after.effectiveLimit())));
  }
}
