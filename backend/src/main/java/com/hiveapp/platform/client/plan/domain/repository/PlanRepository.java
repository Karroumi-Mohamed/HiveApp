package com.hiveapp.platform.client.plan.domain.repository;
import com.hiveapp.platform.client.plan.domain.entity.Plan;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.Optional;
public interface PlanRepository extends JpaRepository<Plan, UUID> {
    Optional<Plan> findByCode(String code);
}
