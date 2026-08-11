package com.hiveapp.platform.client.account.service;

import com.hiveapp.platform.client.company.domain.repository.CompanyRepository;
import com.hiveapp.platform.client.plan.service.SubscriptionImpactContributor;
import com.hiveapp.platform.registry.definition.CompanyFeature;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CompanySubscriptionImpactContributor implements SubscriptionImpactContributor {

    private final CompanyRepository companyRepository;

    @Override
    public String featureCode() {
        return CompanyFeature.CODE;
    }

    @Override
    public FeatureUsage featureUsage(UUID accountId) {
        return new FeatureUsage(
                companyRepository.countByAccountIdAndIsActiveTrue(accountId),
                "Active company profiles remain preserved but become unavailable.");
    }
}
