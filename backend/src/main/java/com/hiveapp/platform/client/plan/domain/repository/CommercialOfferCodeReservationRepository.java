package com.hiveapp.platform.client.plan.domain.repository;

import com.hiveapp.platform.client.plan.domain.entity.CommercialOfferCodeReservation;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommercialOfferCodeReservationRepository
    extends JpaRepository<CommercialOfferCodeReservation, UUID> {
  Optional<CommercialOfferCodeReservation> findByNormalizedCodeHash(String hash);

  Optional<CommercialOfferCodeReservation> findByLineageId(UUID lineage);
}
