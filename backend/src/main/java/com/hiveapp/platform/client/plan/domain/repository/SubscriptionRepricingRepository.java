package com.hiveapp.platform.client.plan.domain.repository;

import com.hiveapp.platform.client.plan.domain.entity.SubscriptionRepricing;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SubscriptionRepricingRepository
    extends JpaRepository<SubscriptionRepricing, UUID> {
  @Override
  @EntityGraph(
      attributePaths = {
        "sourcePrice",
        "sourcePrice.plan",
        "sourcePrice.addOn",
        "sourcePrice.quotaPackage",
        "targetPrice"
      })
  org.springframework.data.domain.Page<SubscriptionRepricing> findAll(
      org.springframework.data.domain.Pageable page);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select j from SubscriptionRepricing j where j.id = :id")
  Optional<SubscriptionRepricing> lock(@Param("id") UUID id);
}
