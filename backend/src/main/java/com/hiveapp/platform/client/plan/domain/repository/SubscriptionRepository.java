package com.hiveapp.platform.client.plan.domain.repository;

import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
    Optional<Subscription> findByAccountIdAndStatus(UUID accountId, SubscriptionStatus status);
    // Shortcut for the most common use case
    default Optional<Subscription> findActiveByAccountId(UUID accountId) {
        return findByAccountIdAndStatus(accountId, SubscriptionStatus.ACTIVE);
    }
}
