package com.hiveapp.platform.client.plan.api;

import com.hiveapp.platform.client.plan.dto.SubscriptionDto;
import com.hiveapp.platform.client.plan.dto.UpdateSubscriptionOverridesRequest;
import com.hiveapp.platform.client.plan.mapper.SubscriptionMapper;
import com.hiveapp.platform.client.plan.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/subscriptions")
@RequiredArgsConstructor
public class SubscriptionAdminController {

    private final SubscriptionService subscriptionService;
    private final SubscriptionMapper subscriptionMapper;

    @GetMapping("/account/{accountId}")
    public SubscriptionDto get(@PathVariable UUID accountId) {
        return subscriptionMapper.toDto(subscriptionService.getSubscription(accountId));
    }

    @PostMapping("/account/{accountId}")
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionDto create(@PathVariable UUID accountId, @RequestParam String planCode) {
        return subscriptionMapper.toDto(subscriptionService.createSubscription(accountId, planCode));
    }

    @PatchMapping("/account/{accountId}/overrides")
    public SubscriptionDto updateOverrides(@PathVariable UUID accountId,
                                           @Valid @RequestBody UpdateSubscriptionOverridesRequest request) {
        var sub = subscriptionService.updateOverrides(
                accountId,
                request.featureCodes(),
                request.quotaOverrides()
        );
        return subscriptionMapper.toDto(sub);
    }
}
