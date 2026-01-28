package com.hiveapp.plan.domain.repository;

import com.hiveapp.plan.domain.entity.PlanFeature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public interface PlanFeatureRepository extends JpaRepository<PlanFeature, UUID> {

    List<PlanFeature> findByPlanId(UUID planId);

    @Query("SELECT pf.featureId FROM PlanFeature pf WHERE pf.plan.id = :planId")
    Set<UUID> findFeatureIdsByPlanId(@Param("planId") UUID planId);

    void deleteByPlanIdAndFeatureId(UUID planId, UUID featureId);
}
