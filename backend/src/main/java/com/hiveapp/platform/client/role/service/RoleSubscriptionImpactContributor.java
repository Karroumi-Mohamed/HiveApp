package com.hiveapp.platform.client.role.service;

import com.hiveapp.platform.client.plan.service.SubscriptionImpactContributor;
import com.hiveapp.platform.client.role.domain.constant.RoleStatus;
import com.hiveapp.platform.client.role.domain.repository.RoleRepository;
import com.hiveapp.platform.registry.definition.WorkspaceRolesFeature;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RoleSubscriptionImpactContributor implements SubscriptionImpactContributor {

    private final RoleRepository roleRepository;

    @Override
    public String featureCode() {
        return WorkspaceRolesFeature.CODE;
    }

    @Override
    public FeatureUsage featureUsage(UUID accountId) {
        return new FeatureUsage(
                roleRepository.countByAccountIdAndIsSystemRoleFalseAndStatus(accountId, RoleStatus.ACTIVE),
                "Active custom roles remain preserved but cannot be managed.");
    }
}
