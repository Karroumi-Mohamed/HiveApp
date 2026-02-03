package com.hiveapp.collaboration.domain.repository;

import com.hiveapp.collaboration.domain.entity.Collaboration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CollaborationRepository extends JpaRepository<Collaboration, UUID> {

    List<Collaboration> findByClientAccountId(UUID clientAccountId);

    List<Collaboration> findByProviderAccountId(UUID providerAccountId);

    List<Collaboration> findByClientAccountIdAndStatus(UUID clientAccountId, String status);

    List<Collaboration> findByProviderAccountIdAndStatus(UUID providerAccountId, String status);

    @Query("SELECT c FROM Collaboration c LEFT JOIN FETCH c.collaborationPermissions WHERE c.id = :id")
    Optional<Collaboration> findByIdWithPermissions(@Param("id") UUID id);

    @Query("SELECT c FROM Collaboration c WHERE c.clientAccountId = :clientAccountId AND c.providerAccountId = :providerAccountId AND c.companyId = :companyId AND c.status != 'revoked'")
    Optional<Collaboration> findActiveOrPending(
            @Param("clientAccountId") UUID clientAccountId,
            @Param("providerAccountId") UUID providerAccountId,
            @Param("companyId") UUID companyId
    );

    @Query("SELECT c FROM Collaboration c LEFT JOIN FETCH c.collaborationPermissions WHERE c.providerAccountId = :providerAccountId AND c.status = 'active'")
    List<Collaboration> findActiveByProviderWithPermissions(@Param("providerAccountId") UUID providerAccountId);
}
