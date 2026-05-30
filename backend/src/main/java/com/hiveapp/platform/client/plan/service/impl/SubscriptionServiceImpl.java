package com.hiveapp.platform.client.plan.service.impl;

import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.platform.client.plan.dto.SubscriptionOverrides;
import com.hiveapp.platform.client.plan.service.BillingConfigurationValidator;
import com.hiveapp.platform.client.plan.service.BillingCalculator;
import com.hiveapp.platform.client.plan.service.SubscriptionService;
import com.hiveapp.platform.client.plan.service.SubscriptionOverrideReader;
import com.hiveapp.platform.registry.definition.ClientSubscriptionFeature;
import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.definition.service.ClientWorkspaceFeatureService;
import com.hiveapp.shared.exception.InvalidStateException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import com.hiveapp.shared.quota.QuotaOverride;
import dev.karroumi.permissionizer.PermissionNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@PermissionNode(key = ClientSubscriptionFeature.KEY, description = "Subscription Management")
public class SubscriptionServiceImpl extends ClientWorkspaceFeatureService implements SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final AccountRepository accountRepository;
    private final BillingCalculator billingCalculator;
    private final SubscriptionOverrideReader subscriptionOverrideReader;
    private final BillingConfigurationValidator billingConfigurationValidator;

    @Override
    protected FeatureDefinition featureDefinition() {
        return ClientSubscriptionFeature.definition();
    }

    @Override
    @PermissionNode(key = "read", description = "View my subscription")
    public Subscription getSubscription(UUID accountId) {
        return subscriptionRepository.findActiveByAccountId(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription", "accountId", accountId));
    }

    @Override
    @Transactional
    public Subscription createSubscription(UUID accountId, String planCode) {
        var account = accountRepository.findByIdForSubscriptionUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));
        var plan = planRepository.findByCode(planCode)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", "code", planCode));
        if (!plan.isActive()) {
            throw new InvalidStateException("Inactive plans cannot be assigned to an account.");
        }

        var usableSubscriptions = subscriptionRepository.findAllByAccountIdAndStatusIn(
                accountId,
                List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING)
        );
        boolean alreadyActiveOnPlan = usableSubscriptions.stream()
                .anyMatch(subscription -> subscription.getStatus() == SubscriptionStatus.ACTIVE
                        && subscription.getPlan().getCode().equals(plan.getCode()));
        if (alreadyActiveOnPlan) {
            throw new InvalidStateException("Account is already subscribed to plan " + plan.getCode() + ".");
        }
        usableSubscriptions.forEach(subscription -> subscription.setStatus(SubscriptionStatus.CANCELLED));
        subscriptionRepository.saveAllAndFlush(usableSubscriptions);

        Subscription sub = new Subscription();
        sub.setAccount(account);
        sub.setPlan(plan);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setCustomOverrides(subscriptionOverrideReader.write(SubscriptionOverrides.empty()));
        sub.setCurrentPrice(plan.getPrice());
        return subscriptionRepository.saveAndFlush(sub);
    }

    @Override
    @Transactional
    public Subscription updateOverrides(UUID accountId,
                                        Set<String> featureCodes,
                                        List<QuotaOverride> quotaOverrides) {
        var sub = getSubscription(accountId);
        billingConfigurationValidator.validateSubscriptionOverrides(featureCodes, quotaOverrides);

        var overrides = new SubscriptionOverrides(
                featureCodes != null ? featureCodes : Set.of(),
                quotaOverrides != null ? quotaOverrides : List.of()
        );
        sub.setCustomOverrides(subscriptionOverrideReader.write(overrides));
        sub.setCurrentPrice(billingCalculator.calculate(sub));
        return subscriptionRepository.save(sub);
    }
}
