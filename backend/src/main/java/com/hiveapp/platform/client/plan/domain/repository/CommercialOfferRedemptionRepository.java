package com.hiveapp.platform.client.plan.domain.repository;
import com.hiveapp.platform.client.plan.domain.entity.CommercialOfferRedemption;
import com.hiveapp.platform.client.plan.domain.constant.CommercialOfferRedemptionStatus;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface CommercialOfferRedemptionRepository extends JpaRepository<CommercialOfferRedemption,UUID>{
 @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @Query("select redemption from CommercialOfferRedemption redemption where redemption.id=:id")
 Optional<CommercialOfferRedemption> lockById(@Param("id") UUID id);
 @Query("select redemption.id from CommercialOfferRedemption redemption where redemption.status=:status and redemption.subscriptionOperationId is not null order by redemption.reservedAt, redemption.id")
 Page<UUID> findIdsByStatusWithOperation(@Param("status") CommercialOfferRedemptionStatus status,Pageable pageable);
 @EntityGraph(attributePaths={"offer"}) Optional<CommercialOfferRedemption> findByAccount_IdAndIdempotencyKeyHash(UUID accountId,String hash);
 long countByOfferLineageIdAndAccount_IdAndStatusIn(UUID lineage,UUID accountId,Collection<CommercialOfferRedemptionStatus> statuses);
 long countByOfferLineageIdAndStatus(UUID lineage,CommercialOfferRedemptionStatus status);
 Page<CommercialOfferRedemption> findAllByOfferLineageId(UUID lineage,Pageable pageable);
 Page<CommercialOfferRedemption> findAllByAccount_Id(UUID accountId,Pageable pageable);
 Optional<CommercialOfferRedemption> findByIdAndAccount_Id(UUID id,UUID accountId);
}
