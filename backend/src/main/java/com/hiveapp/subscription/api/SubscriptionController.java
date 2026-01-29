package com.hiveapp.subscription.api;

import com.hiveapp.subscription.domain.dto.CreateSubscriptionRequest;
import com.hiveapp.subscription.domain.dto.SubscriptionResponse;
import com.hiveapp.subscription.domain.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SubscriptionResponse> createSubscription(
            @Valid @RequestBody CreateSubscriptionRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(subscriptionService.createSubscription(request));
    }

    @GetMapping("/account/{accountId}/active")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SubscriptionResponse> getActiveSubscription(@PathVariable UUID accountId) {
        return ResponseEntity.ok(subscriptionService.getActiveSubscription(accountId));
    }

    @GetMapping("/account/{accountId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<SubscriptionResponse>> getSubscriptionsByAccount(@PathVariable UUID accountId) {
        return ResponseEntity.ok(subscriptionService.getSubscriptionsByAccountId(accountId));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> cancelSubscription(@PathVariable UUID id) {
        subscriptionService.cancelSubscription(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/plan/{planId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> changePlan(@PathVariable UUID id, @PathVariable UUID planId) {
        subscriptionService.changePlan(id, planId);
        return ResponseEntity.noContent().build();
    }
}
