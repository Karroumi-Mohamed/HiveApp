package com.hiveapp.platform.registry.domain.repository;

import com.hiveapp.platform.registry.domain.entity.Feature;
import com.hiveapp.platform.registry.domain.constant.FeatureStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.List;
import java.util.Optional;

public interface FeatureRepository extends JpaRepository<Feature, UUID> {
    Optional<Feature> findByCode(String code);
    List<Feature> findAllByStatus(FeatureStatus status);
    List<Feature> findAllByModuleId(UUID moduleId);
}
