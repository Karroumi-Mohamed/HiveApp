package com.hiveapp.platform.client.plan.api;

import com.hiveapp.platform.client.plan.dto.ClientPlanCatalogResponse;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeApplyResponse;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangePreviewResponse;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeRequest;
import com.hiveapp.platform.client.plan.dto.SubscriptionDto;
import com.hiveapp.platform.client.plan.mapper.SubscriptionMapper;
import com.hiveapp.platform.client.plan.service.SubscriptionService;
import com.hiveapp.shared.security.context.HiveAppContextHolder;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
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

    @GetMapping("/catalog")
    public ClientPlanCatalogResponse catalog() {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        return subscriptionService.catalog(accountId);
    }

    @PostMapping("/preview")
    public SubscriptionChangePreviewResponse preview(@Valid @RequestBody SubscriptionChangeRequest request) {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        return subscriptionService.previewChange(accountId, request);
    }

    @PostMapping("/apply")
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionChangeApplyResponse apply(@Valid @RequestBody SubscriptionChangeRequest request) {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        return subscriptionService.applyChange(accountId, request);
    }
}
