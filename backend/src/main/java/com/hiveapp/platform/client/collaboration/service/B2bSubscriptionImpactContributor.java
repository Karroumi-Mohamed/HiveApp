package com.hiveapp.platform.client.collaboration.service;

import com.hiveapp.platform.client.collaboration.domain.constant.CollaborationStatus;
import com.hiveapp.platform.client.collaboration.domain.repository.CollaborationRepository;
import com.hiveapp.platform.client.plan.service.SubscriptionImpactContributor;
import com.hiveapp.platform.registry.definition.B2bFeature;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class B2bSubscriptionImpactContributor implements SubscriptionImpactContributor {

    private final CollaborationRepository collaborationRepository;

    @Override
    public String featureCode() {
        return B2bFeature.CODE;
    }

    @Override
    public FeatureUsage featureUsage(UUID accountId) {
        long incoming = collaborationRepository.findAllByClientAccountId(accountId).stream()
                .filter(this::live)
                .count();
        long outgoing = collaborationRepository.findAllByProviderAccountId(accountId).stream()
                .filter(this::live)
                .count();
        return new FeatureUsage(Math.addExact(incoming, outgoing),
                "Live incoming and outgoing collaborations require B2B access.");
    }

    private boolean live(com.hiveapp.platform.client.collaboration.domain.entity.Collaboration collaboration) {
        return collaboration.getStatus() == CollaborationStatus.PENDING
                || collaboration.getStatus() == CollaborationStatus.ACTIVE
                || collaboration.getStatus() == CollaborationStatus.SUSPENDED;
    }
}
