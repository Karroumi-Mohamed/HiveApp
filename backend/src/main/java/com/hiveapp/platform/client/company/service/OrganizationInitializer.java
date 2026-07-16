package com.hiveapp.platform.client.company.service;

import com.hiveapp.platform.client.account.domain.entity.Company;
import com.hiveapp.platform.client.company.domain.constant.GroupStatus;
import com.hiveapp.platform.client.company.domain.entity.OrganizationGroup;
import com.hiveapp.platform.client.company.domain.repository.OrganizationGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrganizationInitializer {
    private final OrganizationGroupRepository groupRepository;

    public OrganizationGroup initialize(Company company) {
        OrganizationGroup root = new OrganizationGroup();
        root.setCompany(company);
        root.setName("Departments");
        root.setNormalizedName("departments");
        root.setDisplayOrder(0);
        root.setStatus(GroupStatus.ACTIVE);
        root.setPositionSuggestions(List.of());
        return groupRepository.save(root);
    }
}
