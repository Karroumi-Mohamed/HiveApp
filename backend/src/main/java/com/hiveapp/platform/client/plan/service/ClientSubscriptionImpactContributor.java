package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.registry.definition.ClientSubscriptionFeature;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ClientSubscriptionImpactContributor implements SubscriptionImpactContributor {

    @Override
    public String featureCode() {
        return ClientSubscriptionFeature.CODE;
    }

    @Override
    public FeatureUsage featureUsage(UUID accountId) {
        return new FeatureUsage(1L, "Removing subscription management hides this Account's subscription controls.");
    }
}
