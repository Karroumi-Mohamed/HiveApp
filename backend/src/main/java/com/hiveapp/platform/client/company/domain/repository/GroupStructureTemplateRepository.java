package com.hiveapp.platform.client.company.domain.repository;

import com.hiveapp.platform.client.company.domain.constant.GroupStatus;
import com.hiveapp.platform.client.company.domain.constant.GroupTemplateScope;
import com.hiveapp.platform.client.company.domain.entity.GroupStructureTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GroupStructureTemplateRepository extends JpaRepository<GroupStructureTemplate, UUID> {
    List<GroupStructureTemplate> findAllByScopeAndStatus(GroupTemplateScope scope, GroupStatus status);
    List<GroupStructureTemplate> findAllByAccountIdAndScopeAndStatus(UUID accountId, GroupTemplateScope scope, GroupStatus status);
    List<GroupStructureTemplate> findAllByCompanyIdAndScopeAndStatus(UUID companyId, GroupTemplateScope scope, GroupStatus status);
    boolean existsByScopeAndOwnerScopeKeyAndNormalizedName(GroupTemplateScope scope, UUID ownerScopeKey, String normalizedName);
}
