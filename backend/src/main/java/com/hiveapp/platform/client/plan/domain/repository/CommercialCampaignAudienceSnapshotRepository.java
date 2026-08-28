package com.hiveapp.platform.client.plan.domain.repository;

import com.hiveapp.platform.client.plan.domain.entity.CommercialCampaignAudienceSnapshot;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommercialCampaignAudienceSnapshotRepository
    extends JpaRepository<CommercialCampaignAudienceSnapshot, UUID> {

  boolean existsByCampaign_Id(UUID campaignId);

  Optional<CommercialCampaignAudienceSnapshot> findByCampaign_Id(UUID campaignId);

  @EntityGraph(attributePaths = "accountIds")
  @Query(
      "select snapshot from CommercialCampaignAudienceSnapshot snapshot "
          + "where snapshot.campaign.id = :campaignId")
  Optional<CommercialCampaignAudienceSnapshot> findWithAccountsByCampaignId(
      @Param("campaignId") UUID campaignId);

  @Query(
      value =
          "select accountId from CommercialCampaignAudienceSnapshot snapshot join"
              + " snapshot.accountIds accountId where snapshot.campaign.id = :campaignId order by"
              + " accountId",
      countQuery =
          "select count(accountId) from CommercialCampaignAudienceSnapshot snapshot "
              + "join snapshot.accountIds accountId where snapshot.campaign.id = :campaignId")
  Page<UUID> findAccountIds(@Param("campaignId") UUID campaignId, Pageable pageable);

  @Query(
      "select snapshot.campaign.id, snapshot.affectedAccountCount from"
          + " CommercialCampaignAudienceSnapshot snapshot where snapshot.campaign.id in"
          + " :campaignIds")
  List<Object[]> countFrozenAccounts(@Param("campaignIds") Collection<UUID> campaignIds);

  @Query(
      "select count(snapshot) from CommercialCampaignAudienceSnapshot snapshot "
          + "left join snapshot.accountIds accountId where snapshot.campaign.id = :campaignId "
          + "and (snapshot.audienceMode = 'PUBLIC' or accountId = :accountId)")
  long countEligibleAccount(
      @Param("campaignId") UUID campaignId, @Param("accountId") UUID accountId);
}
