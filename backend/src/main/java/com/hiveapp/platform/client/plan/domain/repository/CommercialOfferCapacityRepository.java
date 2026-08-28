package com.hiveapp.platform.client.plan.domain.repository;
import com.hiveapp.platform.client.plan.domain.entity.CommercialOfferCapacity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface CommercialOfferCapacityRepository extends JpaRepository<CommercialOfferCapacity,UUID>{@Lock(LockModeType.PESSIMISTIC_WRITE)@Query("select c from CommercialOfferCapacity c where c.lineageId=:lineage")Optional<CommercialOfferCapacity> lockByLineage(@Param("lineage")UUID lineage);Optional<CommercialOfferCapacity> findByLineageId(UUID lineage);}
