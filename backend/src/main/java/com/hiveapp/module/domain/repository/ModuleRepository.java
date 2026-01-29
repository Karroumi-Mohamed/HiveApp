package com.hiveapp.module.domain.repository;

import com.hiveapp.module.domain.entity.Module;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ModuleRepository extends JpaRepository<Module, UUID> {

    Optional<Module> findByCode(String code);

    boolean existsByCode(String code);

    List<Module> findByIsActiveTrueOrderBySortOrderAsc();

    @Query("SELECT m FROM Module m LEFT JOIN FETCH m.features WHERE m.isActive = true ORDER BY m.sortOrder")
    List<Module> findAllActiveWithFeatures();
}
