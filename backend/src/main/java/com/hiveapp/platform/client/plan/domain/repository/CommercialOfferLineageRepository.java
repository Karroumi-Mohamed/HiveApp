package com.hiveapp.platform.client.plan.domain.repository;

import com.hiveapp.platform.client.plan.domain.entity.CommercialOfferLineage;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommercialOfferLineageRepository
    extends JpaRepository<CommercialOfferLineage, UUID> {
  boolean existsByBusinessCode(String code);
}
