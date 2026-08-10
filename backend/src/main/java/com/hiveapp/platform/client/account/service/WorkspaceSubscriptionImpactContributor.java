package com.hiveapp.platform.client.account.service;

import com.hiveapp.platform.client.account.domain.repository.CompanyRepository;
import com.hiveapp.platform.client.member.domain.repository.MemberRepository;
import com.hiveapp.platform.client.plan.service.SubscriptionImpactContributor;
import com.hiveapp.platform.registry.definition.WorkspaceFeature;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.OptionalLong;
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

    @Override
    public OptionalLong quotaUsage(UUID accountId, String resource) {
        if (WorkspaceFeature.MEMBERS.equals(resource)) {
            return OptionalLong.of(memberRepository.countByAccountIdAndIsActiveTrue(accountId));
        }
        if (WorkspaceFeature.COMPANIES.equals(resource)) {
            return OptionalLong.of(companyRepository.countByAccountIdAndIsActiveTrue(accountId));
        }
        return OptionalLong.empty();
    }
}
