package com.hiveapp.platform.client.plan.domain.repository;

import com.hiveapp.platform.client.plan.domain.entity.SubscriptionLifecycleEvent;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionLifecycleEventRepository
        extends JpaRepository<SubscriptionLifecycleEvent, UUID> {
    Page<SubscriptionLifecycleEvent> findAllByAccountId(UUID accountId, Pageable pageable);
}
