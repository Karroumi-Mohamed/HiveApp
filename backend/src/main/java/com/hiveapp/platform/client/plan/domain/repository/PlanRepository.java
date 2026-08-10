package com.hiveapp.platform.client.plan.domain.repository;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;
import java.util.Optional;
public interface PlanRepository extends JpaRepository<Plan, UUID> {
    Optional<Plan> findByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select plan from Plan plan where plan.id = :planId")
    Optional<Plan> findByIdForUpdate(@Param("planId") UUID planId);

    long countBySourcePlan_Id(UUID planId);

    @Query("select coalesce(max(plan.revisionNumber), 0) from Plan plan where plan.lineageId = :lineageId")
    int findMaximumRevisionNumber(@Param("lineageId") UUID lineageId);
}
