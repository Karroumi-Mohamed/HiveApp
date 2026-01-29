package com.hiveapp.module.domain.repository;

import com.hiveapp.module.domain.entity.Feature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface FeatureRepository extends JpaRepository<Feature, UUID> {

    Optional<Feature> findByCode(String code);

    boolean existsByCode(String code);

    List<Feature> findByModuleIdAndIsActiveTrue(UUID moduleId);

    @Query("SELECT f FROM Feature f WHERE f.id IN :ids")
    List<Feature> findByIds(@Param("ids") Set<UUID> ids);

    @Query("SELECT f FROM Feature f JOIN FETCH f.module WHERE f.id IN :ids")
    List<Feature> findByIdsWithModule(@Param("ids") Set<UUID> ids);

    @Query("SELECT f.id FROM Feature f WHERE f.module.id = :moduleId AND f.isActive = true")
    Set<UUID> findFeatureIdsByModuleId(@Param("moduleId") UUID moduleId);
}
