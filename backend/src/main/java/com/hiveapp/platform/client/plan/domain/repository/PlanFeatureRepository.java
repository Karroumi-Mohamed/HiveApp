package com.hiveapp.platform.client.plan.domain.repository;

import com.hiveapp.platform.client.plan.domain.entity.PlanFeature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.UUID;

public interface PlanFeatureRepository extends JpaRepository<PlanFeature, UUID> {
    @Query("SELECT COUNT(pf) > 0 FROM PlanFeature pf JOIN pf.feature f JOIN Permission p ON p.feature = f WHERE pf.plan.id = :planId AND p.code = :permissionCode")
    boolean existsByPlanIdAndPermissionCode(UUID planId, String permissionCode);
}
