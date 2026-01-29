package com.hiveapp.subscription.domain.repository;

import com.hiveapp.subscription.domain.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findByAccountIdAndStatus(UUID accountId, String status);

    Optional<Subscription> findTopByAccountIdOrderByCreatedAtDesc(UUID accountId);

    List<Subscription> findByAccountId(UUID accountId);

    List<Subscription> findByStatus(String status);
}
