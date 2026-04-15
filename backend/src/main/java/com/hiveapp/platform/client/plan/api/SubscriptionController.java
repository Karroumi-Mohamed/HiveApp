package com.hiveapp.platform.client.plan.api;

import com.hiveapp.platform.client.plan.dto.SubscriptionDto;
import com.hiveapp.platform.client.plan.mapper.SubscriptionMapper;
import com.hiveapp.platform.client.plan.service.SubscriptionService;
import com.hiveapp.shared.security.context.HiveAppContextHolder;
import dev.karroumi.permissionizer.PermissionNode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
@PermissionNode(key = "subscription", description = "Subscription Management")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final SubscriptionMapper subscriptionMapper;

    @GetMapping("/me")
    @PermissionNode(key = "read", description = "View my subscription")
    public SubscriptionDto getMySubscription() {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        var sub = subscriptionService.getSubscription(accountId);
        return subscriptionMapper.toDto(sub);
    }
}
