package com.hiveapp.platform.client.company.service;

import com.hiveapp.platform.client.company.domain.constant.GroupStatus;
import com.hiveapp.platform.client.company.domain.repository.OrganizationGroupRepository;
import com.hiveapp.platform.client.plan.service.SubscriptionImpactContributor;
import com.hiveapp.platform.registry.definition.OrganizationFeature;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrganizationSubscriptionImpactContributor implements SubscriptionImpactContributor {

    private final OrganizationGroupRepository groupRepository;

    @Override
    public String featureCode() {
        return OrganizationFeature.CODE;
    }

    @Override
    public FeatureUsage featureUsage(UUID accountId) {
        return new FeatureUsage(
                groupRepository.countByCompanyAccountIdAndStatus(accountId, GroupStatus.ACTIVE),
                "Active organization groups and their placements remain preserved but unavailable.");
    }
}
