package com.hiveapp.platform.client.plan.domain.repository;

import com.hiveapp.platform.client.plan.domain.constant.CommercialOfferRedemptionStatus;
import com.hiveapp.platform.client.plan.domain.entity.CommercialOfferRedemption;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;

public interface CommercialOfferRedemptionRepository
    extends JpaRepository<CommercialOfferRedemption, UUID>,
        JpaSpecificationExecutor<CommercialOfferRedemption> {

  @Override
  @EntityGraph(attributePaths = {"offer"})
  Page<CommercialOfferRedemption> findAll(
      org.springframework.data.jpa.domain.Specification<CommercialOfferRedemption> specification,
      Pageable pageable);
  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select redemption from CommercialOfferRedemption redemption where redemption.id=:id")
  Optional<CommercialOfferRedemption> lockById(@Param("id") UUID id);

  @Query(
      "select redemption.id from CommercialOfferRedemption redemption where"
          + " redemption.status='RESERVED' and ((redemption.subscriptionOperationId is null and"
          + " (redemption.applicationLeaseExpiresAt<=:now or exists(select operation.id from"
          + " SubscriptionChangeOperation operation where"
          + " operation.offerRedemptionId=redemption.id))) or (redemption.subscriptionOperationId"
          + " is not null and (exists(select operation.id from SubscriptionChangeOperation"
          + " operation where operation.id=redemption.subscriptionOperationId and operation.status"
          + " in :terminalStatuses) or (redemption.applicationLeaseExpiresAt<=:now and not"
          + " exists(select operation.id from SubscriptionChangeOperation operation where"
          + " operation.id=redemption.subscriptionOperationId))))) order by redemption.reservedAt,"
          + " redemption.id")
  Page<UUID> findActionableIds(
      @Param("now") java.time.Instant now,
      @Param("terminalStatuses")
          Collection<com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeStatus>
              terminalStatuses,
      Pageable pageable);

  @Query("select redemption.account.id from CommercialOfferRedemption redemption where redemption.id=:id")
  Optional<UUID> findAccountIdById(@Param("id") UUID id);

  @EntityGraph(attributePaths = {"offer"})
  Optional<CommercialOfferRedemption> findByAccount_IdAndIdempotencyKeyHash(
      UUID accountId, String hash);

  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @EntityGraph(attributePaths = {"offer"})
  @Query(
      "select redemption from CommercialOfferRedemption redemption where"
          + " redemption.account.id=:accountId and redemption.idempotencyKeyHash=:hash")
  Optional<CommercialOfferRedemption> lockByAccountIdAndIdempotencyKeyHash(
      @Param("accountId") UUID accountId, @Param("hash") String hash);

  @EntityGraph(attributePaths = {"offer"})
  Optional<CommercialOfferRedemption> findByIdAndAccount_Id(UUID id, UUID accountId);

  @EntityGraph(attributePaths = {"offer"})
  Optional<CommercialOfferRedemption> findByIdAndOfferLineageId(UUID id, UUID offerLineageId);

  long countByOfferLineageIdAndAccount_IdAndStatusIn(
      UUID lineage, UUID accountId, Collection<CommercialOfferRedemptionStatus> statuses);

  long countByOfferLineageIdAndStatus(UUID lineage, CommercialOfferRedemptionStatus status);

  @Query(
      "select redemption.status, count(redemption) from CommercialOfferRedemption redemption "
          + "where redemption.offerLineageId=:lineage group by redemption.status")
  List<Object[]> countStatusesByOfferLineageId(@Param("lineage") UUID lineage);

  @EntityGraph(attributePaths = {"offer"})
  Page<CommercialOfferRedemption> findAllByOfferLineageId(UUID lineage, Pageable pageable);

  @EntityGraph(attributePaths = {"offer"})
  Page<CommercialOfferRedemption> findAllByAccount_Id(UUID accountId, Pageable pageable);

  @EntityGraph(attributePaths = {"offer", "account"})
  @Query(
      "select redemption from CommercialOfferRedemption redemption "
          + "where redemption.offerLineageId=:offerLineageId")
  Page<CommercialOfferRedemption> findAllWithIdentitiesByOfferLineageId(
      @Param("offerLineageId") UUID offerLineageId, Pageable pageable);

  @EntityGraph(attributePaths = {"account"})
  @Query(
      "select redemption from CommercialOfferRedemption redemption "
          + "where redemption.offerLineageId=:offerLineageId and redemption.id in :ids")
  List<CommercialOfferRedemption> findAllWithIdentitiesByOfferLineageIdAndIdIn(
      @Param("offerLineageId") UUID offerLineageId, @Param("ids") Collection<UUID> ids);
}
