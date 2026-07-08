package com.hiveapp.platform.client.collaboration.domain.repository;
import com.hiveapp.platform.client.collaboration.domain.entity.Collaboration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;
import java.util.List;
import java.util.Optional;
import com.hiveapp.platform.client.collaboration.domain.constant.CollaborationStatus;

public interface CollaborationRepository extends JpaRepository<Collaboration, UUID> {
    List<Collaboration> findAllByClientAccountId(UUID accountId);
    List<Collaboration> findAllByProviderAccountId(UUID accountId);
    Optional<Collaboration> findByIdAndProviderAccountId(UUID id, UUID providerAccountId);

    @Query("select collaboration from Collaboration collaboration " +
           "where collaboration.id = :id and " +
           "(collaboration.clientAccount.id = :accountId or collaboration.providerAccount.id = :accountId)")
    Optional<Collaboration> findParticipantById(
            @Param("id") UUID id,
            @Param("accountId") UUID accountId);

    Optional<Collaboration> findByClientAccountIdAndProviderAccountIdAndCompanyIdAndStatus(UUID clientAccountId, UUID providerAccountId, UUID companyId, CollaborationStatus status);
}
