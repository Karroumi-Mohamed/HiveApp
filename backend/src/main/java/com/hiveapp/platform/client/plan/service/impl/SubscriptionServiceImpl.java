package com.hiveapp.platform.client.plan.service.impl;

import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.domain.repository.PlanRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepository;
import com.hiveapp.platform.client.plan.dto.SubscriptionOverrides;
import com.hiveapp.platform.client.plan.service.BillingCalculator;
import com.hiveapp.platform.client.plan.service.SubscriptionService;
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
@PermissionNode(key = "subscription", description = "Subscription Management")
public class SubscriptionServiceImpl implements SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final AccountRepository accountRepository;
    private final BillingCalculator billingCalculator;

    @Override
    @PermissionNode(key = "read", description = "View my subscription")
    public Subscription getSubscription(UUID accountId) {
        return subscriptionRepository.findActiveByAccountId(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription", "accountId", accountId));
    }

    @Override
    @Transactional
    public Subscription createSubscription(UUID accountId, String planCode) {
        var account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));
        var plan = planRepository.findByCode(planCode)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", "code", planCode));

        Subscription sub = new Subscription();
        sub.setAccount(account);
        sub.setPlan(plan);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setCurrentPrice(plan.getPrice());
        return subscriptionRepository.save(sub);
    }

    @Override
    @Transactional
    public Subscription updateOverrides(UUID accountId,
                                        Set<String> featureCodes,
                                        List<QuotaOverride> quotaOverrides) {
        var sub = getSubscription(accountId);

        var overrides = new SubscriptionOverrides(
                featureCodes != null ? featureCodes : Set.of(),
                quotaOverrides != null ? quotaOverrides : List.of()
        );
        sub.setCustomOverrides(overrides);
        sub.setCurrentPrice(billingCalculator.calculate(sub));
        return subscriptionRepository.save(sub);
    }
}
