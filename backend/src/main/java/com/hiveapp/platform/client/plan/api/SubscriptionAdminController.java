package com.hiveapp.platform.client.plan.api;

import com.hiveapp.platform.admin.service.AdminSubscriptionService;
import com.hiveapp.platform.client.plan.dto.SubscriptionDto;
import com.hiveapp.platform.client.plan.dto.UpdateSubscriptionOverridesRequest;
import com.hiveapp.platform.client.plan.mapper.SubscriptionMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/subscriptions")
@RequiredArgsConstructor
public class SubscriptionAdminController {

    private final AdminSubscriptionService adminSubscriptionService;
    private final SubscriptionMapper subscriptionMapper;

    @GetMapping("/account/{accountId}")
    public SubscriptionDto get(@PathVariable UUID accountId) {
        return subscriptionMapper.toDto(adminSubscriptionService.getSubscription(accountId));
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
