package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.*;
import com.hiveapp.platform.client.plan.domain.entity.*;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.dto.PlanSubscriberViewModels.*;
import com.hiveapp.shared.exception.*;
import jakarta.persistence.*;
import jakarta.persistence.criteria.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Fixed, paginated presets over current subscriptions, never all historical paid periods. */
@Service
@RequiredArgsConstructor
public class PlanSubscriberViews {
  private final PlanRepository plans;
  private final EntityManager em;

  @Transactional(readOnly = true)
  public Page<Subscriber> list(
      UUID planId,
      View view,
      String search,
      SubscriptionStatus status,
      String currency,
      BillingCycle cycle,
      Pageable page) {
    if (page.getPageNumber() < 0 || page.getPageSize() < 1 || page.getPageSize() > 100)
      throw new InvalidRequestException("Choose a page size between 1 and 100.");
    if (search != null && search.length() > 200)
      throw new InvalidRequestException("Search must not exceed 200 characters.");
    if (currency != null && !currency.matches("[A-Z]{3}"))
      throw new InvalidRequestException("Choose a three-letter currency.");
    Plan source =
        plans
            .findById(planId)
            .orElseThrow(() -> new ResourceNotFoundException("Plan", "id", planId));
    var cb = em.getCriteriaBuilder();
    var query = cb.createTupleQuery();
    var root = query.from(Subscription.class);
    var account = root.join("account");
    var plan = root.join("plan");
    query.multiselect(
        root.get("id"),
        account.get("id"),
        account.get("name"),
        plan.get("id"),
        plan.get("revisionNumber"),
        root.get("status"),
        root.get("currentPrice"),
        root.get("currentPriceCurrencyCode"),
        root.get("snapshotBillingCycle"),
        root.get("currentPeriodEnd"));
    query.where(filters(cb, query, root, source, view, search, status, currency, cycle));
    query.orderBy(cb.asc(cb.lower(account.get("name"))), cb.asc(account.get("id")));
    var rows =
        em.createQuery(query)
            .setFirstResult(Math.toIntExact(page.getOffset()))
            .setMaxResults(page.getPageSize())
            .getResultList();
    var count = cb.createQuery(Long.class);
    var counted = count.from(Subscription.class);
    count
        .select(cb.count(counted))
        .where(filters(cb, count, counted, source, view, search, status, currency, cycle));
    long total = em.createQuery(count).getSingleResult();
    return new PageImpl<>(
        rows.stream()
            .map(
                row ->
                    new Subscriber(
                        row.get(0, UUID.class),
                        row.get(1, UUID.class),
                        row.get(2, String.class),
                        row.get(3, UUID.class),
                        row.get(4, Number.class).longValue(),
                        row.get(5, SubscriptionStatus.class),
                        row.get(6, BigDecimal.class),
                        row.get(7, String.class),
                        row.get(8, BillingCycle.class),
                        row.get(9, Instant.class)))
            .toList(),
        page,
        total);
  }

  private Predicate[] filters(
      CriteriaBuilder cb,
      CriteriaQuery<?> query,
      Root<Subscription> root,
      Plan source,
      View view,
      String search,
      SubscriptionStatus status,
      String currency,
      BillingCycle cycle) {
    List<Predicate> conditions = new ArrayList<>();
    conditions.add(cb.equal(root.get("plan").get("lineageId"), source.getLineageId()));
    conditions.add(cb.isNotNull(root.get("currentAccountId")));
    if (view == View.CURRENT_VERSION)
      conditions.add(cb.equal(root.get("plan").get("id"), source.getId()));
    if (view == View.OTHER_VERSIONS)
      conditions.add(cb.notEqual(root.get("plan").get("id"), source.getId()));
    if (status != null) conditions.add(cb.equal(root.get("status"), status));
    if (currency != null) conditions.add(cb.equal(root.get("currentPriceCurrencyCode"), currency));
    if (cycle != null) conditions.add(cb.equal(root.get("snapshotBillingCycle"), cycle));
    if (search != null && !search.isBlank()) {
      String escaped =
          search
              .trim()
              .toLowerCase(Locale.ROOT)
              .replace("!", "!!")
              .replace("%", "!%")
              .replace("_", "!_");
      conditions.add(cb.like(cb.lower(root.get("account").get("name")), "%" + escaped + "%", '!'));
    }
    if (view == View.PENDING || view == View.NEEDS_REVIEW) {
      var candidates = query.subquery(Integer.class);
      var item = candidates.from(SubscriptionChangeJobItem.class);
      var job = item.join("job");
      List<Predicate> pending = new ArrayList<>();
      pending.add(cb.equal(item.get("account").get("id"), root.get("account").get("id")));
      pending.add(cb.equal(job.get("planLineageId"), source.getLineageId()));
      pending.add(cb.notEqual(job.get("status"), SubscriptionChangeJobStatus.CANCELLED));
      if (view == View.PENDING) {
        pending.add(
            item.get("status")
                .in(
                    SubscriptionChangeJobItemStatus.READY,
                    SubscriptionChangeJobItemStatus.WAITING));
        pending.add(cb.isNotNull(job.get("confirmedAt")));
      } else {
        pending.add(
            item.get("status")
                .in(
                    SubscriptionChangeJobItemStatus.CONFLICT,
                    SubscriptionChangeJobItemStatus.FAILED));
        // A later successfully applied instruction resolves the older conflict in this preset.
        var newer = candidates.subquery(Integer.class);
        var resolution = newer.from(SubscriptionChangeJobItem.class);
        newer
            .select(cb.literal(1))
            .where(
                cb.equal(resolution.get("account").get("id"), root.get("account").get("id")),
                cb.equal(resolution.get("job").get("planLineageId"), source.getLineageId()),
                cb.equal(resolution.get("status"), SubscriptionChangeJobItemStatus.APPLIED),
                cb.greaterThanOrEqualTo(resolution.get("completedAt"), item.get("createdAt")));
        pending.add(cb.not(cb.exists(newer)));
      }
      candidates.select(cb.literal(1)).where(pending.toArray(Predicate[]::new));
      conditions.add(cb.exists(candidates));
    }
    return conditions.toArray(Predicate[]::new);
  }
}
