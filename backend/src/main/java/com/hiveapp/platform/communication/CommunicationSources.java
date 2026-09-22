package com.hiveapp.platform.communication;

import static com.hiveapp.platform.communication.CommunicationModels.*;

import com.hiveapp.platform.client.plan.domain.entity.*;
import com.hiveapp.platform.client.plan.domain.repository.*;
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

  public List<String> allowedPermissions() {
    return java.util.stream.Stream.concat(
            List.of(
                "platform.subscription.read_content_notices",
                "platform.subscription.read_price_notices")
                .stream(),
            catalog.all().stream()
                .map(NotificationDefinition::requiredPermission)
                .filter(Objects::nonNull))
        .distinct()
        .filter(p -> PermissionGuard.has(new Permission(p)))
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
    e.setTopic(Topic.COMMERCIAL);
    e.setMessageTitle(notice.getPlanName());
    e.setMessageBody("V" + notice.getSourceVersion() + " → V" + notice.getTargetVersion());
    e.setRequiredPermission("platform.subscription.read_content_notices");
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
    e.setTopic(Topic.BILLING);
    e.setMessageTitle("Tarif de votre abonnement");
    e.setMessageBody("Un changement de tarif a été préparé. Consultez son état et ses conditions.");
    e.setRequiredPermission("platform.subscription.read_price_notices");
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
    return result;
  }

  public boolean isInactiveState(String state) {
    return "CANCELLED".equals(state) || "WITHDRAWN".equals(state) || "APPLIED".equals(state);
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
