package com.hiveapp.platform.client.plan.domain.repository;
import com.hiveapp.platform.client.plan.domain.entity.CommercialOfferCodeReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface CommercialOfferCodeReservationRepository extends JpaRepository<CommercialOfferCodeReservation,UUID>{Optional<CommercialOfferCodeReservation> findByNormalizedCodeHash(String hash);Optional<CommercialOfferCodeReservation> findByLineageId(UUID lineage);}
