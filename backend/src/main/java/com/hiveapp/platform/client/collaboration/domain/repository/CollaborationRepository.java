package com.hiveapp.platform.client.collaboration.domain.repository;
import com.hiveapp.platform.client.collaboration.domain.entity.Collaboration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;
import java.util.List;
import java.util.Optional;
import java.util.Collection;
import java.time.Instant;
import com.hiveapp.platform.client.collaboration.domain.constant.CollaborationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

public interface CollaborationRepository extends JpaRepository<Collaboration, UUID> {
    List<Collaboration> findAllByClientAccountId(UUID accountId);
    List<Collaboration> findAllByProviderAccountId(UUID accountId);
    Optional<Collaboration> findByIdAndProviderAccountId(UUID id, UUID providerAccountId);

    Optional<Collaboration> findFirstByClientAccountIdAndProviderAccountIdAndCompanyIdAndStatusIn(
            UUID clientAccountId,
            UUID providerAccountId,
            UUID companyId,
            Collection<CollaborationStatus> statuses);

    boolean existsByClientAccountIdAndProviderAccountIdAndCompanyIdAndStatusIn(
            UUID clientAccountId,
            UUID providerAccountId,
            UUID companyId,
            Collection<CollaborationStatus> statuses);

    @Query("select collaboration from Collaboration collaboration " +
           "where collaboration.id = :id and " +
           "(collaboration.clientAccount.id = :accountId or collaboration.providerAccount.id = :accountId)")
    Optional<Collaboration> findParticipantById(
            @Param("id") UUID id,
            @Param("accountId") UUID accountId);

    Optional<Collaboration> findByClientAccountIdAndProviderAccountIdAndCompanyIdAndStatus(UUID clientAccountId, UUID providerAccountId, UUID companyId, CollaborationStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select collaboration from Collaboration collaboration "
            + "where collaboration.status = :status "
            + "and collaboration.automaticResumeAt <= :now")
    List<Collaboration> findDueAutomaticResumesForUpdate(
            @Param("status") CollaborationStatus status,
            @Param("now") Instant now);
}
