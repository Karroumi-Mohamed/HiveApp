package com.hiveapp.platform.registry.domain.repository;

import com.hiveapp.platform.registry.domain.entity.FeatureOperationalChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FeatureOperationalChangeRepository extends JpaRepository<FeatureOperationalChange, UUID> {
    List<FeatureOperationalChange> findAllByFeatureIdOrderByCreatedAtDesc(UUID featureId);
}
