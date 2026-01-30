package com.hiveapp.permission.domain.repository;

import com.hiveapp.permission.domain.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    Optional<Permission> findByCode(String code);

    boolean existsByCode(String code);

    List<Permission> findByFeatureId(UUID featureId);

    @Query("SELECT p FROM Permission p WHERE p.featureId IN :featureIds")
    List<Permission> findByFeatureIds(@Param("featureIds") Set<UUID> featureIds);

    @Query("SELECT p FROM Permission p WHERE p.id IN :ids")
    List<Permission> findByIds(@Param("ids") Set<UUID> ids);

    @Query("SELECT p FROM Permission p WHERE p.code IN :codes")
    List<Permission> findByCodes(@Param("codes") Set<String> codes);

    @Query("SELECT p FROM Permission p WHERE p.featureId IN (SELECT f.id FROM com.hiveapp.module.domain.entity.Feature f WHERE f.module.id IN :moduleIds AND f.isActive = true)")
    List<Permission> findByModuleIds(@Param("moduleIds") Set<UUID> moduleIds);
}
