package com.hiveapp.platform.client.plan.domain.repository;

import com.hiveapp.platform.client.plan.domain.entity.BillingProviderEvent;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;

public interface BillingProviderEventRepository extends JpaRepository<BillingProviderEvent, UUID>,
        JpaSpecificationExecutor<BillingProviderEvent> {
    Optional<BillingProviderEvent> findByProviderAndEventId(String provider, String eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from BillingProviderEvent event where event.id = :id")
    Optional<BillingProviderEvent> findByIdForUpdate(@Param("id") UUID id);

    @Query("select event.processingStatus as value, count(event) as total, "
            + "min(event.createdAt) as oldest "
            + "from BillingProviderEvent event group by event.processingStatus")
    List<CountByStatus> countByStatus();

    interface CountByStatus {
        com.hiveapp.platform.client.plan.domain.constant.BillingProviderEventStatus getValue();
        long getTotal();
        Instant getOldest();
    }
}
