package com.hiveapp.platform.client.member.service;

import com.hiveapp.platform.client.member.domain.repository.MemberRepository;
import com.hiveapp.platform.client.plan.service.SubscriptionImpactContributor;
import com.hiveapp.platform.registry.definition.StaffFeature;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.OptionalLong;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class StaffSubscriptionImpactContributor implements SubscriptionImpactContributor {

    private final MemberRepository memberRepository;

    @Override
    public String featureCode() {
        return StaffFeature.CODE;
    }

    @Override
    public FeatureUsage featureUsage(UUID accountId) {
        return new FeatureUsage(
                memberRepository.countByAccountIdAndIsActiveTrueAndIsOwnerFalse(accountId),
                "Active non-owner members require staff management access.");
    }

    @Override
    public OptionalLong quotaUsage(UUID accountId, String resource) {
        if (StaffFeature.MEMBERS.equals(resource)) {
            return OptionalLong.of(memberRepository.countByAccountIdAndIsActiveTrue(accountId));
        }
        return OptionalLong.empty();
    }
}
