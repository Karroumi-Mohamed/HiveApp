package com.hiveapp.platform.client.plan.api;

import com.hiveapp.platform.client.plan.dto.SubscriptionDto;
import com.hiveapp.platform.client.plan.mapper.SubscriptionMapper;
import com.hiveapp.platform.client.plan.service.SubscriptionService;
import com.hiveapp.shared.security.context.HiveAppContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final SubscriptionMapper subscriptionMapper;

    @GetMapping("/me")
    public SubscriptionDto getMySubscription() {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        return subscriptionMapper.toDto(subscriptionService.getSubscription(accountId));
    }
}
