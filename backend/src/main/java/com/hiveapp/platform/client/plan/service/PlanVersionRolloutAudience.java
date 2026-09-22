package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.entity.*;
import com.hiveapp.platform.client.plan.dto.PlanVersionRolloutModels.*;
import com.hiveapp.shared.exception.InvalidRequestException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Scalar, bounded audience freeze; never load every subscription's snapshots into an HTTP request.
 */
@Component
@RequiredArgsConstructor
public class PlanVersionRolloutAudience {
  public static final int MAX_TARGETS = 10000;
  private final EntityManager em;

  public record Member(UUID accountId, UUID subscriptionId, UUID sourcePlanId, boolean excluded) {}

  public List<Member> freeze(Plan source, Request request) {
    if (request.scope() == null || request.audience() == null)
      throw new InvalidRequestException("Choose the source scope and audience explicitly.");
    var selected = unique(request.accountIds(), "Selected Accounts");
    var excluded = unique(request.excludedAccountIds(), "Excluded Accounts");
    if ((request.audience() == Audience.SELECTED) != !selected.isEmpty())
      throw new InvalidRequestException("Only a selected audience requires explicit Account IDs.");
    boolean hasFilters =
        (request.search() != null && !request.search().isBlank())
            || request.currency() != null
            || request.billingCycle() != null;
    if (request.audience() == Audience.ALL && hasFilters)
      throw new InvalidRequestException(
          "Use the filtered audience when supplying search, currency or cycle filters.");
    if (request.statuses().stream().anyMatch(Objects::isNull))
      throw new InvalidRequestException("Subscription statuses cannot be null.");
    var cb = em.getCriteriaBuilder();
    var query = cb.createTupleQuery();
    var root = query.from(Subscription.class);
    var account = root.join("account");
    var plan = root.join("plan");
    List<Predicate> filters = new ArrayList<>();
    filters.add(cb.isNotNull(root.get("currentAccountId")));
    filters.add(
        request.scope() == Scope.FAMILY
            ? cb.equal(plan.get("lineageId"), source.getLineageId())
            : cb.equal(plan.get("id"), source.getId()));
    filters.add(root.get("status").in(request.statuses()));
    if (!selected.isEmpty()) filters.add(account.get("id").in(selected));
    if (request.currency() != null)
      filters.add(cb.equal(root.get("currentPriceCurrencyCode"), request.currency()));
    if (request.billingCycle() != null)
      filters.add(cb.equal(root.get("snapshotBillingCycle"), request.billingCycle()));
    if (request.search() != null && !request.search().isBlank()) {
      String escaped =
          request
              .search()
              .trim()
              .toLowerCase(Locale.ROOT)
              .replace("!", "!!")
              .replace("%", "!%")
              .replace("_", "!_");
      filters.add(cb.like(cb.lower(account.get("name")), "%" + escaped + "%", '!'));
    }
    query
        .multiselect(account.get("id"), root.get("id"), plan.get("id"))
        .where(filters.toArray(Predicate[]::new))
        .orderBy(cb.asc(account.get("id")));
    var rows = em.createQuery(query).setMaxResults(MAX_TARGETS + 1).getResultList();
    if (rows.size() > MAX_TARGETS)
      throw new InvalidRequestException(
          "This rollout exceeds 10,000 Accounts. Narrow the audience explicitly.");
    if (rows.isEmpty())
      throw new InvalidRequestException("The chosen audience contains no current subscriptions.");
    var result =
        rows.stream()
            .map(
                row ->
                    new Member(
                        row.get(0, UUID.class),
                        row.get(1, UUID.class),
                        row.get(2, UUID.class),
                        excluded.contains(row.get(0, UUID.class))))
            .toList();
    if (!selected.isEmpty()) {
      if (!selected.equals(new HashSet<>(result.stream().map(Member::accountId).toList())))
        throw new InvalidRequestException(
            "Some selected Accounts no longer match this source version or the chosen filters.");
    }
    return result;
  }

  private Set<UUID> unique(List<UUID> ids, String label) {
    if (ids.size() > MAX_TARGETS
        || ids.stream().anyMatch(Objects::isNull)
        || new HashSet<>(ids).size() != ids.size())
      throw new InvalidRequestException(label + " must be unique and contain at most 10,000 IDs.");
    return new HashSet<>(ids);
  }
}
