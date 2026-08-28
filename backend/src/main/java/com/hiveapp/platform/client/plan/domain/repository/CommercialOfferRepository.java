package com.hiveapp.platform.client.plan.domain.repository;
import com.hiveapp.platform.client.plan.domain.entity.CommercialOffer;
import com.hiveapp.platform.client.plan.domain.constant.CommercialOfferStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface CommercialOfferRepository extends JpaRepository<CommercialOffer,UUID>,JpaSpecificationExecutor<CommercialOffer>{
 @EntityGraph(attributePaths={"lineage","lineage.campaign","lineage.owner","lineage.owner.user","sourceOffer"}) @Query("select o from CommercialOffer o where o.id=:id") Optional<CommercialOffer> findDetailById(@Param("id")UUID id);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select o from CommercialOffer o join fetch o.lineage lineage join fetch lineage.campaign join fetch lineage.owner owner join fetch owner.user where o.id=:id") Optional<CommercialOffer> findForUpdate(@Param("id")UUID id);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select o from CommercialOffer o where o.lineage.id=:lineage order by o.revisionNumber,o.id") List<CommercialOffer> lockLineage(@Param("lineage")UUID lineage);
 Page<CommercialOffer> findAllByLineage_Id(UUID lineage,Pageable pageable);
 @Query("select coalesce(max(o.revisionNumber),0) from CommercialOffer o where o.lineage.id=:lineage") int maxRevision(@Param("lineage")UUID lineage);
 Optional<CommercialOffer> findFirstByLineage_IdAndStatus(UUID lineage,CommercialOfferStatus status);
 @Query("select o from CommercialOffer o join CommercialOfferCodeReservation r on r.lineageId=o.lineage.id where r.normalizedCodeHash=:hash and o.status='PUBLISHED' order by o.revisionNumber desc") List<CommercialOffer> findPublishedByReservedCodeHash(@Param("hash")String hash,Pageable pageable);
 @Query(value="select distinct o from CommercialOffer o join o.lineage l join l.campaign c join CommercialCampaignAudienceSnapshot s on s.campaign=c left join s.accountIds accountId where o.status='PUBLISHED' and l.discovery='CATALOG' and l.acceptance='CLIENT_OR_OPERATOR' and o.startsAt<=:now and o.endsAt>:now and c.status='ACTIVE' and (s.audienceMode='PUBLIC' or accountId=:accountId) and exists(select sub.id from Subscription sub where sub.account.id=:accountId and sub.status in ('ACTIVE','TRIALING'))",
 countQuery="select count(distinct o) from CommercialOffer o join o.lineage l join l.campaign c join CommercialCampaignAudienceSnapshot s on s.campaign=c left join s.accountIds accountId where o.status='PUBLISHED' and l.discovery='CATALOG' and l.acceptance='CLIENT_OR_OPERATOR' and o.startsAt<=:now and o.endsAt>:now and c.status='ACTIVE' and (s.audienceMode='PUBLIC' or accountId=:accountId) and exists(select sub.id from Subscription sub where sub.account.id=:accountId and sub.status in ('ACTIVE','TRIALING'))")
 Page<CommercialOffer> findEligibleClientCatalogue(@Param("accountId")UUID accountId,@Param("now")java.time.Instant now,Pageable pageable);
}
