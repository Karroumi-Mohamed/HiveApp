package com.hiveapp.platform.client.plan.domain.repository;

import com.hiveapp.platform.client.plan.domain.entity.PlanFeature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface PlanFeatureRepository extends JpaRepository<PlanFeature, UUID> {

    // Used by PlanPolicy — checks if a plan grants access to a permission via its features
    @Query("SELECT COUNT(pf) > 0 FROM PlanFeature pf JOIN pf.feature f JOIN f.permissions p " +
           "WHERE pf.plan.id = :planId AND p.code = :permissionCode")
    boolean existsByPlanIdAndPermissionCode(UUID planId, String permissionCode);

    // Used by QuotaEnforcer — loads a plan's quota config for a specific feature
    Optional<PlanFeature> findByPlanIdAndFeature_Code(UUID planId, String featureCode);
}
