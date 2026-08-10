package com.hiveapp.platform.client.plan.domain.repository;

import com.hiveapp.platform.client.plan.domain.entity.SubscriptionCheckout;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SubscriptionCheckoutRepository extends JpaRepository<SubscriptionCheckout, UUID> {

    Optional<SubscriptionCheckout> findByChangeOperationId(UUID operationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select checkout from SubscriptionCheckout checkout where checkout.id = :id")
    Optional<SubscriptionCheckout> findByIdForUpdate(@Param("id") UUID id);
}
