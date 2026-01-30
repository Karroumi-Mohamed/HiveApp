package com.hiveapp.company.domain.repository;

import com.hiveapp.company.domain.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanyRepository extends JpaRepository<Company, UUID> {

    List<Company> findByAccountIdAndIsActiveTrue(UUID accountId);

    List<Company> findByAccountId(UUID accountId);

    long countByAccountIdAndIsActiveTrue(UUID accountId);

    @Query("SELECT c FROM Company c LEFT JOIN FETCH c.companyModules WHERE c.id = :id")
    Optional<Company> findByIdWithModules(@Param("id") UUID id);

    @Query("SELECT DISTINCT c FROM Company c LEFT JOIN FETCH c.companyModules WHERE c.accountId = :accountId AND c.isActive = true")
    List<Company> findByAccountIdWithModules(@Param("accountId") UUID accountId);
}
