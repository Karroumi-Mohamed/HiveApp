package com.hiveapp.platform.registry.domain.repository;

import com.hiveapp.platform.registry.domain.entity.Feature;
import com.hiveapp.platform.registry.domain.constant.FeatureStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.UUID;
import java.util.List;
import java.util.Collection;
import java.util.Optional;

public interface FeatureRepository extends JpaRepository<Feature, UUID> {
    Optional<Feature> findByCode(String code);
    List<Feature> findAllByStatus(FeatureStatus status);
    List<Feature> findAllByModuleId(UUID moduleId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select feature from Feature feature where feature.id = :id")
    Optional<Feature> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select feature from Feature feature where feature.id in :ids order by feature.id")
    List<Feature> findAllByIdInForUpdate(@Param("ids") Collection<UUID> ids);
}
