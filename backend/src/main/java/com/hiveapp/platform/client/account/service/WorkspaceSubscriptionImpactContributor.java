package com.hiveapp.platform.client.account.service;

import com.hiveapp.platform.client.company.domain.repository.CompanyRepository;
import com.hiveapp.platform.client.member.domain.repository.MemberRepository;
import com.hiveapp.platform.client.plan.service.SubscriptionImpactContributor;
import com.hiveapp.platform.registry.definition.WorkspaceFeature;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class WorkspaceSubscriptionImpactContributor implements SubscriptionImpactContributor {

    private final MemberRepository memberRepository;
    private final CompanyRepository companyRepository;

    @Override
    public String featureCode() {
        return WorkspaceFeature.CODE;
    }

    @Override
    public FeatureUsage featureUsage(UUID accountId) {
        long members = memberRepository.countByAccountIdAndIsActiveTrue(accountId);
        long companies = companyRepository.countByAccountIdAndIsActiveTrue(accountId);
        return new FeatureUsage(Math.addExact(members, companies),
                "Active workspace members and companies require workspace access.");
    }
}
