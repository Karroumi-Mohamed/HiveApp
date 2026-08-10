package com.hiveapp.platform.client.plan.domain.repository;

import com.hiveapp.platform.client.plan.domain.entity.AddOnFeature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AddOnFeatureRepository extends JpaRepository<AddOnFeature, UUID> {
    List<AddOnFeature> findAllByAddOnId(UUID addOnId);
    Optional<AddOnFeature> findByAddOnIdAndFeature_Code(UUID addOnId, String featureCode);
}
