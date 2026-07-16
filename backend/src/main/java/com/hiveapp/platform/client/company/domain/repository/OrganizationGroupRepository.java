package com.hiveapp.platform.client.company.domain.repository;

import com.hiveapp.platform.client.company.domain.entity.OrganizationGroup;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationGroupRepository extends JpaRepository<OrganizationGroup, UUID> {
    List<OrganizationGroup> findAllByCompanyIdOrderByDisplayOrderAscNameAsc(UUID companyId);
    List<OrganizationGroup> findAllByCompanyIdAndParentScopeKeyOrderByDisplayOrderAscNameAsc(UUID companyId, UUID parentScopeKey);
    Optional<OrganizationGroup> findByIdAndCompanyAccountId(UUID id, UUID accountId);
    boolean existsByCompanyIdAndParentScopeKeyAndNormalizedName(UUID companyId, UUID parentScopeKey, String normalizedName);
    boolean existsByCompanyIdAndParentScopeKeyAndNormalizedNameAndIdNot(UUID companyId, UUID parentScopeKey, String normalizedName, UUID id);
    long countByParentId(UUID parentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<OrganizationGroup> findForUpdateByCompanyIdAndParentScopeKey(UUID companyId, UUID parentScopeKey);
}
