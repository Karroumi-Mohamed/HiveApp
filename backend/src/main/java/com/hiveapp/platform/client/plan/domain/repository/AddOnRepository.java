package com.hiveapp.platform.client.plan.domain.repository;

import com.hiveapp.platform.client.plan.domain.entity.AddOn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AddOnRepository extends JpaRepository<AddOn, UUID> {
    Optional<AddOn> findByCode(String code);
    List<AddOn> findAllByCodeIn(Collection<String> codes);

    @EntityGraph(attributePaths = {"features", "features.feature"})
    List<AddOn> findAllByOrderByNameAscRevisionNumberDesc();

    @EntityGraph(attributePaths = {"features", "features.feature"})
    Optional<AddOn> findDetailedById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select addOn from AddOn addOn where addOn.id = :addOnId")
    Optional<AddOn> findByIdForUpdate(@Param("addOnId") UUID addOnId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select addOn from AddOn addOn where addOn.lineageId = :lineageId order by addOn.revisionNumber")
    List<AddOn> findLineageForUpdate(@Param("lineageId") UUID lineageId);

    @Query("select coalesce(max(addOn.revisionNumber), 0) from AddOn addOn where addOn.lineageId = :lineageId")
    int findMaximumRevisionNumber(@Param("lineageId") UUID lineageId);
}
