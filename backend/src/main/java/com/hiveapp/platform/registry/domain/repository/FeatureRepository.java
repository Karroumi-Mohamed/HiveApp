package com.hiveapp.platform.registry.domain.repository;
import com.hiveapp.platform.registry.domain.entity.Feature;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface FeatureRepository extends JpaRepository<Feature, UUID> {}
