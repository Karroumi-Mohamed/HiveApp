package com.hiveapp.platform.client.plan.domain.repository;

import com.hiveapp.platform.client.plan.domain.constant.SubscriptionPeriodStatus;
import com.hiveapp.platform.client.plan.domain.entity.SubscriptionPeriod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionPeriodRepository extends JpaRepository<SubscriptionPeriod, UUID> {

    Optional<SubscriptionPeriod> findBySubscriptionIdAndStatus(
            UUID subscriptionId, SubscriptionPeriodStatus status);

    List<SubscriptionPeriod> findAllBySubscriptionAccountIdOrderByStartsAtDesc(UUID accountId);
}
