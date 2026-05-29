package com.hiveapp.platform.client.plan.domain.repository;

import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    // Returns latest ACTIVE — guards against accidental duplicates (admin error, race condition).
    // Production: enforce at DB level with partial unique index: UNIQUE (account_id) WHERE status = 'ACTIVE'
    Optional<Subscription> findTopByAccountIdAndStatusOrderByCreatedAtDesc(UUID accountId, SubscriptionStatus status);

    List<Subscription> findAllByAccountIdAndStatusIn(UUID accountId, Collection<SubscriptionStatus> statuses);

    default Optional<Subscription> findActiveByAccountId(UUID accountId) {
        return findTopByAccountIdAndStatusOrderByCreatedAtDesc(accountId, SubscriptionStatus.ACTIVE);
    }

    default Optional<Subscription> findByAccountIdAndStatus(UUID accountId, SubscriptionStatus status) {
        return findTopByAccountIdAndStatusOrderByCreatedAtDesc(accountId, status);
    }
}
