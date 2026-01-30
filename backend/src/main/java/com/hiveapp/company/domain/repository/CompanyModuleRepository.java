package com.hiveapp.company.domain.repository;

import com.hiveapp.company.domain.entity.CompanyModule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface CompanyModuleRepository extends JpaRepository<CompanyModule, UUID> {

    List<CompanyModule> findByCompanyIdAndIsActiveTrue(UUID companyId);

    Optional<CompanyModule> findByCompanyIdAndModuleId(UUID companyId, UUID moduleId);

    @Query("SELECT cm.moduleId FROM CompanyModule cm WHERE cm.company.id = :companyId AND cm.isActive = true")
    Set<UUID> findActiveModuleIdsByCompanyId(@Param("companyId") UUID companyId);
}
