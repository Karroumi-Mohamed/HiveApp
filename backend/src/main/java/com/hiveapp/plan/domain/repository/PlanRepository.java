package com.hiveapp.plan.domain.repository;

import com.hiveapp.plan.domain.entity.Plan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlanRepository extends JpaRepository<Plan, UUID> {

    Optional<Plan> findByCode(String code);

    boolean existsByCode(String code);

    List<Plan> findByIsActiveTrueOrderByPriceAsc();

    @Query("SELECT p FROM Plan p LEFT JOIN FETCH p.planFeatures WHERE p.id = :id")
    Optional<Plan> findByIdWithFeatures(UUID id);

    @Query("SELECT p FROM Plan p LEFT JOIN FETCH p.planFeatures WHERE p.isActive = true ORDER BY p.price ASC")
    List<Plan> findAllActiveWithFeatures();
}
