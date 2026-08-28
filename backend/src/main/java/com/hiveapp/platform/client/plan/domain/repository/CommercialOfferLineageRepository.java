package com.hiveapp.platform.client.plan.domain.repository;

import com.hiveapp.platform.client.plan.domain.entity.CommercialOfferLineage;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommercialOfferLineageRepository
    extends JpaRepository<CommercialOfferLineage, UUID> {
  boolean existsByBusinessCode(String code);

  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @EntityGraph(attributePaths = {"owner", "owner.user"})
  @Query("select lineage from CommercialOfferLineage lineage where lineage.id=:id")
  Optional<CommercialOfferLineage> findByIdForUpdate(@Param("id") UUID id);
}
