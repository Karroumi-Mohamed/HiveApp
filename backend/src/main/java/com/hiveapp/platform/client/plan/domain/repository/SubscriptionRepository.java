package com.hiveapp.platform.client.plan.domain.repository;

import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.Instant;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID>,
        JpaSpecificationExecutor<Subscription> {

    long countByStatus(SubscriptionStatus status);

    // Returns latest ACTIVE — guards against accidental duplicates (admin error, race condition).
    // Production: enforce at DB level with partial unique index: UNIQUE (account_id) WHERE status = 'ACTIVE'
    Optional<Subscription> findTopByAccountIdAndStatusOrderByCreatedAtDesc(UUID accountId, SubscriptionStatus status);

    List<Subscription> findAllByAccountIdAndStatusIn(UUID accountId, Collection<SubscriptionStatus> statuses);

    Optional<Subscription> findTopByAccountIdAndStatusInOrderByCreatedAtDesc(
            UUID accountId,
            Collection<SubscriptionStatus> statuses);

    List<Subscription> findAllByPlan_IdAndStatusInOrderByCreatedAtDesc(UUID planId, Collection<SubscriptionStatus> statuses);

    @Query(value = "select subscription from Subscription subscription "
            + "join subscription.account account "
            + "where subscription.plan.id = :planId "
            + "and (:status is null or subscription.status = :status) "
            + "and (:search is null or lower(account.name) like lower(concat('%', :search, '%')))",
            countQuery = "select count(subscription) from Subscription subscription "
                    + "join subscription.account account "
                    + "where subscription.plan.id = :planId "
                    + "and (:status is null or subscription.status = :status) "
                    + "and (:search is null or lower(account.name) like lower(concat('%', :search, '%')))")
    Page<Subscription> searchPlanSubscribers(
            @Param("planId") UUID planId,
            @Param("status") SubscriptionStatus status,
            @Param("search") String search,
            Pageable pageable);

    @Query(value = "select subscription from Subscription subscription "
            + "join subscription.account account join account.owner owner "
            + "where subscription.plan.id = :planId and lower(owner.email) = lower(:ownerEmail)",
            countQuery = "select count(subscription) from Subscription subscription "
                    + "join subscription.account account join account.owner owner "
                    + "where subscription.plan.id = :planId and lower(owner.email) = lower(:ownerEmail)")
    Page<Subscription> findPlanSubscribersByOwnerEmail(
            @Param("planId") UUID planId,
            @Param("ownerEmail") String ownerEmail,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select subscription from Subscription subscription "
            + "where subscription.status in :statuses and subscription.currentPeriodEnd <= :cutoff")
    List<Subscription> findDueUsableForUpdate(
            @Param("statuses") Collection<SubscriptionStatus> statuses,
            @Param("cutoff") Instant cutoff);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select subscription from Subscription subscription "
            + "where subscription.status = :status and subscription.graceEndsAt <= :cutoff")
    List<Subscription> findGraceExpiredForUpdate(
            @Param("status") SubscriptionStatus status,
            @Param("cutoff") Instant cutoff);

    @Query("select subscription from Subscription subscription "
            + "where subscription.account.id = :accountId and ("
            + "subscription.status in :ordinaryStatuses or "
            + "(subscription.status = :pastDueStatus and subscription.graceEndsAt > :at))")
    Optional<Subscription> findEntitledAt(
            @Param("accountId") UUID accountId,
            @Param("ordinaryStatuses") Collection<SubscriptionStatus> ordinaryStatuses,
            @Param("pastDueStatus") SubscriptionStatus pastDueStatus,
            @Param("at") Instant at);

    long countByPlan_Id(UUID planId);

    long countByPlan_IdAndStatus(UUID planId, SubscriptionStatus status);

    long countByPlan_IdAndStatusIn(UUID planId, Collection<SubscriptionStatus> statuses);

    @Query(value = "select distinct subscription.account.id from Subscription subscription "
            + "where subscription.plan.id = :planId and subscription.status in :statuses "
            + "order by subscription.account.id asc",
            countQuery = "select count(distinct subscription.account.id) from Subscription subscription "
                    + "where subscription.plan.id = :planId and subscription.status in :statuses")
    Page<UUID> findDistinctAccountIdsByPlanAndStatuses(
            @Param("planId") UUID planId,
            @Param("statuses") Collection<SubscriptionStatus> statuses,
            Pageable pageable);

    @Query("select count(distinct subscription.account.id) from Subscription subscription "
            + "where subscription.plan.id = :planId and subscription.status in :statuses")
    long countDistinctAccountsByPlanAndStatuses(
            @Param("planId") UUID planId,
            @Param("statuses") Collection<SubscriptionStatus> statuses);

    @Query("select count(distinct subscription.account.id) from Subscription subscription "
            + "where subscription.account.id in :accountIds and subscription.status in :statuses "
            + "and subscription.currentPriceCurrencyCode is not null "
            + "and subscription.currentPriceCurrencyCode not in :currencyCodes")
    long countAudienceCurrencyMismatches(
            @Param("accountIds") Collection<UUID> accountIds,
            @Param("statuses") Collection<SubscriptionStatus> statuses,
            @Param("currencyCodes") Collection<String> currencyCodes);

    @Query("""
            select subscription.plan.id, count(subscription)
            from Subscription subscription
            where subscription.plan.id in :planIds and subscription.status in :statuses
            group by subscription.plan.id
            """)
    List<Object[]> countCurrentByPlanIds(
            @Param("planIds") Collection<UUID> planIds,
            @Param("statuses") Collection<SubscriptionStatus> statuses);

    @Query("""
            select subscription.plan.id, count(subscription)
            from Subscription subscription
            where subscription.plan.id in :planIds
            group by subscription.plan.id
            """)
    List<Object[]> countHistoryByPlanIds(@Param("planIds") Collection<UUID> planIds);

    default Optional<Subscription> findActiveByAccountId(UUID accountId) {
        return findTopByAccountIdAndStatusOrderByCreatedAtDesc(accountId, SubscriptionStatus.ACTIVE);
    }

    @EntityGraph(attributePaths = "currentHoldings")
    @Query("select subscription from Subscription subscription "
            + "where subscription.account.id = :accountId and subscription.status = :status")
    Optional<Subscription> findWithCurrentHoldingsByAccountIdAndStatus(
            @Param("accountId") UUID accountId,
            @Param("status") SubscriptionStatus status);

    /**
     * Mutation-specific lookup for callers that replace the authoritative entitlement snapshot.
     * Loading the normalized holdings in the same query keeps detached test/support workflows safe
     * without burdening ordinary subscription reads with the projection collection.
     */
    default Optional<Subscription> findActiveForSnapshotUpdateByAccountId(UUID accountId) {
        return findWithCurrentHoldingsByAccountIdAndStatus(
                accountId, SubscriptionStatus.ACTIVE);
    }

    default Optional<Subscription> findByAccountIdAndStatus(UUID accountId, SubscriptionStatus status) {
        return findTopByAccountIdAndStatusOrderByCreatedAtDesc(accountId, status);
    }

    default Optional<Subscription> findUsableByAccountId(UUID accountId) {
        return findActiveByAccountId(accountId)
                .or(() -> findByAccountIdAndStatus(accountId, SubscriptionStatus.TRIALING));
    }

    default Optional<Subscription> findCurrentByAccountId(UUID accountId) {
        return findTopByAccountIdAndStatusInOrderByCreatedAtDesc(
                accountId,
                List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING,
                        SubscriptionStatus.PAST_DUE, SubscriptionStatus.SUSPENDED));
    }

    @Override
    @EntityGraph(attributePaths = "plan")
    List<Subscription> findAll(Specification<Subscription> specification, Sort sort);
}
