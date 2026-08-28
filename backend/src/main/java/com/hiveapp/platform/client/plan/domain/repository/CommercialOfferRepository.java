package com.hiveapp.platform.client.plan.domain.repository;

import com.hiveapp.platform.client.plan.domain.constant.CommercialOfferStatus;
import com.hiveapp.platform.client.plan.domain.entity.CommercialOffer;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CommercialOfferRepository
    extends JpaRepository<CommercialOffer, UUID>, JpaSpecificationExecutor<CommercialOffer> {
  @Query("select o.id from CommercialOffer o where o.lineage.id = :lineageId")
  List<UUID> findIdsByLineageId(@Param("lineageId") UUID lineageId);

  @Override
  @EntityGraph(attributePaths = {"lineage", "lineage.campaign"})
  Page<CommercialOffer> findAll(
      org.springframework.data.jpa.domain.Specification<CommercialOffer> specification,
      Pageable pageable);

  @EntityGraph(
      attributePaths = {
        "lineage",
        "lineage.campaign",
        "lineage.owner",
        "lineage.owner.user",
        "sourceOffer"
      })
  @Query("select o from CommercialOffer o where o.id=:id")
  Optional<CommercialOffer> findDetailById(@Param("id") UUID id);

  @EntityGraph(attributePaths = {"lineage", "lineage.campaign"})
  @Query("select o from CommercialOffer o where o.id=:id")
  Optional<CommercialOffer> findOperationsById(@Param("id") UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select o from CommercialOffer o join fetch o.lineage lineage join fetch lineage.campaign"
          + " join fetch lineage.owner owner join fetch owner.user where o.id=:id")
  Optional<CommercialOffer> findForUpdate(@Param("id") UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select o from CommercialOffer o where o.lineage.id=:lineage order by o.revisionNumber,o.id")
  List<CommercialOffer> lockLineage(@Param("lineage") UUID lineage);

  Page<CommercialOffer> findAllByLineage_Id(UUID lineage, Pageable pageable);

  @Query(
      "select coalesce(max(o.revisionNumber),0) from CommercialOffer o where o.lineage.id=:lineage")
  int maxRevision(@Param("lineage") UUID lineage);

  Optional<CommercialOffer> findFirstByLineage_IdAndStatus(
      UUID lineage, CommercialOfferStatus status);

  boolean existsBySourceOffer_Id(UUID sourceOfferId);

  boolean existsByLineage_Campaign_Id(UUID campaignId);

  @Query(
      "select distinct o.lineage.campaign.id from CommercialOffer o where"
          + " o.lineage.campaign.id in :campaignIds")
  Set<UUID> findCampaignIdsWithOffers(@Param("campaignIds") Collection<UUID> campaignIds);

  @Query("select distinct o.sourceOffer.id from CommercialOffer o where o.sourceOffer.id in :ids")
  Set<UUID> findReferencedSourceIds(@Param("ids") Collection<UUID> ids);

  @Query(
      "select distinct o.lineage.id from CommercialOffer o where o.lineage.id in :lineages and"
          + " o.status=:status")
  Set<UUID> findLineageIdsWithStatus(
      @Param("lineages") Collection<UUID> lineages, @Param("status") CommercialOfferStatus status);

  boolean existsByLineage_IdAndStatus(UUID lineageId, CommercialOfferStatus status);

  long countByLineage_Id(UUID lineageId);

  @EntityGraph(attributePaths = {"lineage", "lineage.campaign"})
  @Query(
      "select o from CommercialOffer o join CommercialOfferCodeReservation r on"
          + " r.lineageId=o.lineage.id where r.normalizedCodeHash=:hash and o.status='PUBLISHED'"
          + " order by o.revisionNumber desc")
  List<CommercialOffer> findPublishedByReservedCodeHash(
      @Param("hash") String hash, Pageable pageable);

  @EntityGraph(attributePaths = {"lineage", "lineage.campaign"})
  @Query(
      value =
          "select distinct o from CommercialOffer o join o.lineage l join l.campaign c join"
              + " CommercialOfferCapacity capacity on capacity.lineageId=l.id join"
              + " CommercialCampaignAudienceSnapshot s on s.campaign=c left join s.accountIds"
              + " accountId where o.status='PUBLISHED' and l.discovery='CATALOG' and"
              + " l.acceptance='CLIENT_OR_OPERATOR' and o.startsAt<=:now and o.endsAt>:now and"
              + " c.status='ACTIVE' and (s.audienceMode='PUBLIC' or accountId=:accountId) and"
              + " (l.globalLimit is null or"
              + " capacity.reservedCount+capacity.appliedCount<l.globalLimit) and"
              + " (l.perAccountLimit is null or (select count(redemption) from"
              + " CommercialOfferRedemption redemption where redemption.offerLineageId=l.id and"
              + " redemption.account.id=:accountId and redemption.status in ('RESERVED','APPLIED'))"
              + " <l.perAccountLimit) and exists(select sub.id from Subscription sub where"
              + " sub.account.id=:accountId and sub.status in ('ACTIVE','TRIALING'))",
      countQuery =
          "select count(distinct o) from CommercialOffer o join o.lineage l join l.campaign c join"
              + " CommercialOfferCapacity capacity on capacity.lineageId=l.id join"
              + " CommercialCampaignAudienceSnapshot s on s.campaign=c left join s.accountIds"
              + " accountId where o.status='PUBLISHED' and l.discovery='CATALOG' and"
              + " l.acceptance='CLIENT_OR_OPERATOR' and o.startsAt<=:now and o.endsAt>:now and"
              + " c.status='ACTIVE' and (s.audienceMode='PUBLIC' or accountId=:accountId) and"
              + " (l.globalLimit is null or"
              + " capacity.reservedCount+capacity.appliedCount<l.globalLimit) and"
              + " (l.perAccountLimit is null or (select count(redemption) from"
              + " CommercialOfferRedemption redemption where redemption.offerLineageId=l.id and"
              + " redemption.account.id=:accountId and redemption.status in ('RESERVED','APPLIED'))"
              + " <l.perAccountLimit) and exists(select sub.id from Subscription sub where"
              + " sub.account.id=:accountId and sub.status in ('ACTIVE','TRIALING'))")
  Page<CommercialOffer> findEligibleClientCatalogue(
      @Param("accountId") UUID accountId, @Param("now") java.time.Instant now, Pageable pageable);
}
