package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyBlocker;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyTargetKind;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.domain.entity.CommercialPolicy;
import com.hiveapp.platform.client.plan.domain.repository.CommercialPolicyRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.platform.client.plan.dto.CommercialPolicyViews;
import com.hiveapp.shared.api.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Backend-owned audience resolution; client-submitted hidden populations are never accepted. */
@Service
@RequiredArgsConstructor
public class CommercialPolicyAudienceResolver {

    public static final int ACTIVATION_ACCOUNT_LIMIT = 10_000;
    private static final int RESOLUTION_PAGE_SIZE = 500;
    public static final List<SubscriptionStatus> CURRENT_SUBSCRIPTION_STATUSES = List.of(
            SubscriptionStatus.TRIALING,
            SubscriptionStatus.ACTIVE,
            SubscriptionStatus.PAST_DUE,
            SubscriptionStatus.SUSPENDED);

    private final AccountRepository accountRepository;
    private final CommercialPolicyRepository policyRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final CommercialSegmentAudienceResolver segmentAudienceResolver;

    public Resolution resolveExact(CommercialPolicy policy) {
        CommercialSegmentAudienceResolver.FrozenAudience segmentAudience = segmentAudience(policy);
        if (policy.getTargetKind() == CommercialPolicyTargetKind.SEGMENT && !segmentAudience.available()) {
            return new Resolution(List.of(), List.of(CommercialPolicyBlocker.SEGMENT_RESOLUTION_UNAVAILABLE));
        }
        long count = count(policy, segmentAudience);
        if (count > ACTIVATION_ACCOUNT_LIMIT) {
            return new Resolution(List.of(), List.of(CommercialPolicyBlocker.AUDIENCE_EXCEEDS_ACTIVATION_LIMIT));
        }
        List<UUID> ids = switch (policy.getTargetKind()) {
            case ACCOUNT -> policy.getTargetAccount() == null
                    ? List.of() : List.of(policy.getTargetAccount().getId());
            case ACCOUNT_SET -> policyRepository.findExplicitAccountIds(policy.getId());
            case PLAN_REVISION_SUBSCRIBERS -> resolvePlanSubscriberIds(policy.getTargetPlan().getId(), count);
            case SEGMENT -> segmentAudience.accountIds();
        };
        return new Resolution(ids, List.of());
    }

    public CommercialPolicyViews.AudiencePreview preview(
            CommercialPolicy policy,
            int page,
            int size
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw new com.hiveapp.shared.exception.InvalidRequestException(
                    "Page must be non-negative and size must be between 1 and 100.");
        }
        CommercialSegmentAudienceResolver.FrozenAudience segmentAudience = segmentAudience(policy);
        long total = count(policy, segmentAudience);
        List<CommercialPolicyBlocker> blockers = new ArrayList<>();
        if (policy.getTargetKind() == CommercialPolicyTargetKind.SEGMENT && !segmentAudience.available()) {
            blockers.add(CommercialPolicyBlocker.SEGMENT_RESOLUTION_UNAVAILABLE);
        }
        if (total > ACTIVATION_ACCOUNT_LIMIT) {
            blockers.add(CommercialPolicyBlocker.AUDIENCE_EXCEEDS_ACTIVATION_LIMIT);
        }
        List<UUID> pageIds = pageIds(policy, page, size, total, segmentAudience);
        List<CommercialPolicyViews.AudienceAccount> accounts = summaries(pageIds);
        var accountPage = new PageImpl<>(accounts, PageRequest.of(page, size), total);
        return new CommercialPolicyViews.AudiencePreview(
                policy.getId(), policy.getVersion(), policy.getTargetKind(), total,
                ACTIVATION_ACCOUNT_LIMIT, total <= ACTIVATION_ACCOUNT_LIMIT,
                PageResponse.from(accountPage), List.copyOf(blockers));
    }

    public List<CommercialPolicyViews.AudienceAccount> summaries(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        Map<UUID, Account> byId = accountRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));
        return ids.stream().distinct().map(id -> {
            Account account = byId.get(id);
            return account == null
                    ? new CommercialPolicyViews.AudienceAccount(id, null, null, false)
                    : new CommercialPolicyViews.AudienceAccount(
                            account.getId(), account.getName(), account.getSlug(), account.isActive());
        }).toList();
    }

    private long count(
            CommercialPolicy policy,
            CommercialSegmentAudienceResolver.FrozenAudience segmentAudience
    ) {
        return switch (policy.getTargetKind()) {
            case ACCOUNT -> policy.getTargetAccount() == null ? 0 : 1;
            case ACCOUNT_SET -> policyRepository.countExplicitAccounts(policy.getId());
            case PLAN_REVISION_SUBSCRIBERS -> policy.getTargetPlan() == null ? 0
                    : subscriptionRepository.countDistinctAccountsByPlanAndStatuses(
                            policy.getTargetPlan().getId(), CURRENT_SUBSCRIPTION_STATUSES);
            case SEGMENT -> segmentAudience.accountIds().size();
        };
    }

    private List<UUID> pageIds(
            CommercialPolicy policy,
            int page,
            int size,
            long total,
            CommercialSegmentAudienceResolver.FrozenAudience segmentAudience
    ) {
        if (total == 0) return List.of();
        if (policy.getTargetKind() == CommercialPolicyTargetKind.PLAN_REVISION_SUBSCRIBERS) {
            return subscriptionRepository.findDistinctAccountIdsByPlanAndStatuses(
                    policy.getTargetPlan().getId(), CURRENT_SUBSCRIPTION_STATUSES,
                    PageRequest.of(page, size)).getContent();
        }
        List<UUID> all = switch (policy.getTargetKind()) {
            case ACCOUNT -> List.of(policy.getTargetAccount().getId());
            case ACCOUNT_SET -> policyRepository.findExplicitAccountIds(policy.getId());
            case SEGMENT -> segmentAudience.accountIds();
            case PLAN_REVISION_SUBSCRIBERS -> throw new IllegalStateException("Handled above");
        };
        int from = (int) Math.min((long) page * size, all.size());
        int to = Math.min(from + size, all.size());
        return all.subList(from, to);
    }

    public String requireCanonicalSegmentReference(String reference) {
        return segmentAudienceResolver.requireCanonicalActiveReference(reference);
    }

    private CommercialSegmentAudienceResolver.FrozenAudience segmentAudience(CommercialPolicy policy) {
        if (policy.getTargetKind() != CommercialPolicyTargetKind.SEGMENT) {
            return new CommercialSegmentAudienceResolver.FrozenAudience(null, null, List.of(), false);
        }
        try {
            return segmentAudienceResolver.resolveFrozenReference(policy.getSegmentReference());
        } catch (com.hiveapp.shared.exception.InvalidRequestException invalid) {
            return new CommercialSegmentAudienceResolver.FrozenAudience(null, null, List.of(), false);
        }
    }

    private List<UUID> resolvePlanSubscriberIds(UUID planId, long expectedCount) {
        Set<UUID> ids = new LinkedHashSet<>();
        int page = 0;
        while (ids.size() < expectedCount) {
            var result = subscriptionRepository.findDistinctAccountIdsByPlanAndStatuses(
                    planId, CURRENT_SUBSCRIPTION_STATUSES, PageRequest.of(page++, RESOLUTION_PAGE_SIZE));
            ids.addAll(result.getContent());
            if (result.isLast()) break;
        }
        return ids.stream().sorted(Comparator.naturalOrder()).toList();
    }

    public record Resolution(List<UUID> accountIds, List<CommercialPolicyBlocker> blockers) {
        public Resolution {
            accountIds = List.copyOf(accountIds);
            blockers = List.copyOf(blockers);
        }
    }
}
