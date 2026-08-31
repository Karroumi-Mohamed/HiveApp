package com.hiveapp.platform.client.plan.domain.repository;

import com.hiveapp.platform.client.plan.domain.constant.BillingOutboxStatus;
import com.hiveapp.platform.client.plan.domain.constant.BillingOutboxOperation;
import com.hiveapp.platform.client.plan.domain.entity.BillingOutboxCommand;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BillingOutboxCommandRepository extends JpaRepository<BillingOutboxCommand, UUID> {
    @Query("select command.id from BillingOutboxCommand command "
            + "where command.status = :status and command.nextAttemptAt <= :cutoff "
            + "order by command.nextAttemptAt asc, command.id asc")
    List<UUID> findReadyIds(
            @Param("status") BillingOutboxStatus status,
            @Param("cutoff") Instant cutoff,
            Pageable pageable);

    @Query("select command.id from BillingOutboxCommand command "
            + "where command.status = :status and command.claimedAt < :cutoff "
            + "order by command.claimedAt asc, command.id asc")
    List<UUID> findStaleClaimIds(
            @Param("status") BillingOutboxStatus status,
            @Param("cutoff") Instant cutoff,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select command from BillingOutboxCommand command where command.id = :id")
    Optional<BillingOutboxCommand> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select command from BillingOutboxCommand command "
            + "where command.aggregateId = :aggregateId and command.operation = :operation")
    Optional<BillingOutboxCommand> findByAggregateIdAndOperationForUpdate(
            @Param("aggregateId") UUID aggregateId,
            @Param("operation") BillingOutboxOperation operation);
}
