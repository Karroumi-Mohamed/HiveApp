package com.hiveapp.platform.client.plan.domain.repository;

import com.hiveapp.platform.client.plan.domain.constant.CommercialCampaignStatus;
import com.hiveapp.platform.client.plan.domain.entity.CommercialCampaign;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommercialCampaignRepository extends JpaRepository<CommercialCampaign, UUID>,
        JpaSpecificationExecutor<CommercialCampaign> {

    boolean existsByCode(String code);

    @Override
    Page<CommercialCampaign> findAll(Specification<CommercialCampaign> specification, Pageable pageable);

    @EntityGraph(attributePaths = {"sourceCampaign", "owner", "segment", "segmentActivation"})
    @Query("select campaign from CommercialCampaign campaign where campaign.id = :campaignId")
    Optional<CommercialCampaign> findDetailById(@Param("campaignId") UUID campaignId);

    @Query("select campaign.lineageId from CommercialCampaign campaign where campaign.id = :campaignId")
    Optional<UUID> findLineageIdById(@Param("campaignId") UUID campaignId);

    @Query("select campaign.owner.id from CommercialCampaign campaign where campaign.id = :campaignId")
    Optional<UUID> findOwnerAdminUserIdById(@Param("campaignId") UUID campaignId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select campaign from CommercialCampaign campaign where campaign.id = :campaignId")
    Optional<CommercialCampaign> findByIdForUpdate(@Param("campaignId") UUID campaignId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select campaign from CommercialCampaign campaign where campaign.lineageId = :lineageId "
            + "order by campaign.revisionNumber, campaign.id")
    List<CommercialCampaign> findLineageForUpdate(@Param("lineageId") UUID lineageId);

    Page<CommercialCampaign> findAllByLineageId(UUID lineageId, Pageable pageable);

    @Query("select coalesce(max(campaign.revisionNumber), 0) from CommercialCampaign campaign "
            + "where campaign.lineageId = :lineageId")
    int findMaximumRevisionNumber(@Param("lineageId") UUID lineageId);

    @Query("select campaign.lineageId, max(campaign.revisionNumber) from CommercialCampaign campaign "
            + "where campaign.lineageId in :lineageIds group by campaign.lineageId")
    List<Object[]> findMaximumRevisionNumbers(@Param("lineageIds") Collection<UUID> lineageIds);

    @Query("select campaign.id, count(account) from CommercialCampaign campaign "
            + "left join campaign.explicitAccounts account where campaign.id in :campaignIds "
            + "group by campaign.id")
    List<Object[]> countExplicitAccountsByCampaignIds(@Param("campaignIds") Collection<UUID> campaignIds);

    @Query("select account.id from CommercialCampaign campaign join campaign.explicitAccounts account "
            + "where campaign.id = :campaignId order by account.id")
    List<UUID> findExplicitAccountIds(@Param("campaignId") UUID campaignId);

    @Query("select campaign.id from CommercialCampaign campaign where campaign.status = :status "
            + "and campaign.startsAt <= :due order by campaign.startsAt, campaign.id")
    List<UUID> findDueStarts(@Param("status") CommercialCampaignStatus status,
                             @Param("due") Instant due, Pageable pageable);

    @Query("select campaign.id from CommercialCampaign campaign where campaign.status in :statuses "
            + "and campaign.endsAt <= :due order by campaign.endsAt, campaign.id")
    List<UUID> findDueEnds(@Param("statuses") Collection<CommercialCampaignStatus> statuses,
                           @Param("due") Instant due, Pageable pageable);

    @Query("select count(campaign) from CommercialCampaign campaign where campaign.lineageId = :lineageId "
            + "and campaign.status in :statuses and campaign.id <> :campaignId")
    long countOtherLiveRevisions(@Param("lineageId") UUID lineageId,
                                 @Param("campaignId") UUID campaignId,
                                 @Param("statuses") Collection<CommercialCampaignStatus> statuses);

    long countBySegment_Id(UUID segmentId);

    long countBySegment_IdAndStatus(UUID segmentId, CommercialCampaignStatus status);

    boolean existsBySourceCampaign_Id(UUID sourceCampaignId);

    @Query("select campaign.sourceCampaign.id, count(campaign) from CommercialCampaign campaign "
            + "where campaign.sourceCampaign.id in :sourceCampaignIds "
            + "group by campaign.sourceCampaign.id")
    List<Object[]> countDerivedBySourceCampaignIds(
            @Param("sourceCampaignIds") Collection<UUID> sourceCampaignIds);

    @Query("select campaign.segment.id, count(campaign) from CommercialCampaign campaign "
            + "where campaign.segment.id in :segmentIds group by campaign.segment.id")
    List<Object[]> countReferencesBySegmentIds(@Param("segmentIds") Collection<UUID> segmentIds);

    @Query("select campaign.segment.id, count(campaign) from CommercialCampaign campaign "
            + "where campaign.segment.id in :segmentIds and campaign.status = :status "
            + "group by campaign.segment.id")
    List<Object[]> countReferencesBySegmentIdsAndStatus(
            @Param("segmentIds") Collection<UUID> segmentIds,
            @Param("status") CommercialCampaignStatus status);
}
