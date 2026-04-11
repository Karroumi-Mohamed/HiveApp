package com.hiveapp.platform.client.plan.api;

import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/subscriptions")
@RequiredArgsConstructor
public class SubscriptionAdminController {

    private final SubscriptionService subscriptionService;

    @GetMapping("/account/{accountId}")
    public ResponseEntity<Subscription> get(@PathVariable UUID accountId) {
        return ResponseEntity.ok(subscriptionService.getSubscription(accountId));
    }

    @PostMapping("/account/{accountId}")
    public ResponseEntity<Subscription> create(@PathVariable UUID accountId, @RequestParam String planCode) {
        return ResponseEntity.ok(subscriptionService.createSubscription(accountId, planCode));
    }

    @PatchMapping("/account/{accountId}/overrides")
    public ResponseEntity<Void> updateOverrides(@PathVariable UUID accountId, @RequestBody Object overrides) {
        subscriptionService.updateOverrides(accountId, overrides);
        return ResponseEntity.noContent().build();
    }
}
