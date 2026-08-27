package com.hiveapp.platform.client.plan.domain.repository;

import com.hiveapp.platform.client.plan.domain.entity.CommercialSegmentActivation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommercialSegmentActivationRepository
        extends JpaRepository<CommercialSegmentActivation, UUID> {

    @Query("select coalesce(max(activation.activationNumber), 0) "
            + "from CommercialSegmentActivation activation where activation.segment.id = :segmentId")
    int findMaximumActivationNumber(@Param("segmentId") UUID segmentId);

    Optional<CommercialSegmentActivation> findTopBySegment_IdOrderByActivationNumberDesc(UUID segmentId);

    @EntityGraph(attributePaths = "accountIds")
    @Query("select activation from CommercialSegmentActivation activation "
            + "where activation.segment.id = :segmentId "
            + "and activation.activationNumber = (select max(other.activationNumber) "
            + "from CommercialSegmentActivation other where other.segment.id = :segmentId)")
    Optional<CommercialSegmentActivation> findLatestWithAccounts(@Param("segmentId") UUID segmentId);

    @EntityGraph(attributePaths = "accountIds")
    @Query("select activation from CommercialSegmentActivation activation where activation.id = :activationId")
    Optional<CommercialSegmentActivation> findWithAccountsById(@Param("activationId") UUID activationId);

    Optional<CommercialSegmentActivation> findByIdAndSegment_Id(UUID activationId, UUID segmentId);

    Page<CommercialSegmentActivation> findAllBySegment_Id(UUID segmentId, Pageable pageable);

    @Query(value = "select accountId from CommercialSegmentActivation activation "
            + "join activation.accountIds accountId where activation.id = :activationId "
            + "and activation.segment.id = :segmentId order by accountId",
            countQuery = "select count(accountId) from CommercialSegmentActivation activation "
                    + "join activation.accountIds accountId where activation.id = :activationId "
                    + "and activation.segment.id = :segmentId")
    Page<UUID> findSnapshotAccountIds(
            @Param("segmentId") UUID segmentId,
            @Param("activationId") UUID activationId,
            Pageable pageable);

    @Query("select activation.segment.id, activation.affectedAccountCount "
            + "from CommercialSegmentActivation activation where activation.segment.id in :segmentIds "
            + "and activation.activationNumber = (select max(other.activationNumber) "
            + "from CommercialSegmentActivation other where other.segment.id = activation.segment.id)")
    List<Object[]> countLatestSnapshotAccounts(@Param("segmentIds") Collection<UUID> segmentIds);

    @Query("select activation.segment.id, activation.id, activation.activationNumber, "
            + "activation.affectedAccountCount "
            + "from CommercialSegmentActivation activation where activation.segment.id in :segmentIds "
            + "and activation.activationNumber = (select max(other.activationNumber) "
            + "from CommercialSegmentActivation other where other.segment.id = activation.segment.id)")
    List<Object[]> findLatestMetadata(@Param("segmentIds") Collection<UUID> segmentIds);

    boolean existsBySegment_Id(UUID segmentId);
}
