package com.hiveapp.platform.client.plan.api;

import com.hiveapp.platform.admin.service.AdminSubscriptionService;
import com.hiveapp.platform.client.plan.dto.SubscriptionDto;
import com.hiveapp.platform.client.plan.dto.UpdateSubscriptionOverridesRequest;
import com.hiveapp.platform.client.plan.mapper.SubscriptionMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import com.hiveapp.platform.admin.dto.AdminSubscriptionDto;
import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.service.SubscriptionOverrideReader;
import com.hiveapp.platform.client.plan.service.SubscriptionSnapshotReader;

@RestController
@RequestMapping("/api/admin/subscriptions")
@RequiredArgsConstructor
public class SubscriptionAdminController {

    private final AdminSubscriptionService adminSubscriptionService;
    private final SubscriptionMapper subscriptionMapper;
    private final SubscriptionOverrideReader subscriptionOverrideReader;
    private final SubscriptionSnapshotReader subscriptionSnapshotReader;

    @GetMapping("/account/{accountId}")
    public AdminSubscriptionDto get(@PathVariable UUID accountId) {
        return toAdminDto(adminSubscriptionService.getSubscription(accountId));
    }

    private AdminSubscriptionDto toAdminDto(Subscription sub) {
        return new AdminSubscriptionDto(
                sub.getId(),
                sub.getAccount().getId(),
                sub.getAccount().getName(),
                sub.getPlan().getCode(),
                sub.getPlan().getName(),
                sub.getStatus(),
                sub.getCurrentPrice(),
                sub.getCurrentPeriodEnd(),
                subscriptionOverrideReader.read(sub.getCustomOverrides()),
                subscriptionSnapshotReader.read(sub.getEntitlementSnapshot()).orElse(null)
        );
    }

    @PostMapping("/account/{accountId}")
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionDto create(@PathVariable UUID accountId, @RequestParam String planCode) {
        return subscriptionMapper.toDto(adminSubscriptionService.createSubscription(accountId, planCode));
    }

    @PatchMapping("/account/{accountId}/overrides")
    public SubscriptionDto updateOverrides(@PathVariable UUID accountId,
                                           @Valid @RequestBody UpdateSubscriptionOverridesRequest request) {
        return subscriptionMapper.toDto(adminSubscriptionService.updateOverrides(
                accountId,
                request.featureCodes(),
                request.quotaOverrides()
        ));
    }
}
