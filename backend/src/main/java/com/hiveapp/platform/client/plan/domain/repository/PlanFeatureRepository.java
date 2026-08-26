package com.hiveapp.platform.client.plan.domain.repository;

import com.hiveapp.platform.client.plan.domain.entity.PlanFeature;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface PlanFeatureRepository extends JpaRepository<PlanFeature, UUID> {

    // Used by PlanPolicy — checks if a plan grants access to a permission via its features
    @Query("SELECT COUNT(pf) > 0 FROM PlanFeature pf JOIN pf.feature f JOIN f.permissions p " +
           "WHERE pf.plan.id = :planId AND p.code = :permissionCode")
    boolean existsByPlanIdAndPermissionCode(UUID planId, String permissionCode);

    // Used by QuotaEnforcer and BillingCalculator
    Optional<PlanFeature> findByPlanIdAndFeature_Code(UUID planId, String featureCode);

    // Used by PlanAdminService, snapshot assembly, and subscription entitlement resolution.
    // Nearly every caller projects the owning feature code, so the graph loads it up front
    // rather than initializing one proxy per plan feature.
    @EntityGraph(attributePaths = "feature")
    List<PlanFeature> findAllByPlanId(UUID planId);

    @EntityGraph(attributePaths = {"plan", "feature"})
    @Query("select item from PlanFeature item where item.plan.id in :planIds order by item.id")
    List<PlanFeature> findAllByPlanIds(@Param("planIds") Collection<UUID> planIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"plan", "feature"})
    @Query("select item from PlanFeature item where item.plan.id in :planIds order by item.id")
    List<PlanFeature> findAllByPlanIdsForUpdate(@Param("planIds") Collection<UUID> planIds);

    @EntityGraph(attributePaths = {"plan", "feature"})
    @Query("select item from PlanFeature item")
    List<PlanFeature> findAllDetailed();

    @Query("""
            select item.plan.id, count(item),
                   sum(case when item.mode = com.hiveapp.platform.client.plan.domain.constant.PlanFeatureMode.INCLUDED
                       then 1 else 0 end)
            from PlanFeature item
            where item.plan.id in :planIds
            group by item.plan.id
            """)
    List<Object[]> countCompositionByPlanIds(
            @org.springframework.data.repository.query.Param("planIds") Collection<UUID> planIds);
}
