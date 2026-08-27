package com.hiveapp.platform.client.plan.api;

import com.hiveapp.platform.client.plan.dto.ClientPlanCatalogResponse;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeApplyResponse;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeApplyRequest;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangePreviewResponse;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeRequest;
import com.hiveapp.platform.client.plan.dto.SubscriptionDto;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeOperationDto;
import com.hiveapp.platform.client.plan.service.SubscriptionService;
import com.hiveapp.shared.security.context.HiveAppContextHolder;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping("/me")
    public SubscriptionDto getMySubscription() {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        return subscriptionService.getMySubscription(accountId);
    }

    @GetMapping("/catalog")
    public ClientPlanCatalogResponse catalog() {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        return subscriptionService.catalog(accountId);
    }

    @PostMapping("/preview")
    public SubscriptionChangePreviewResponse preview(@Valid @RequestBody SubscriptionChangeRequest request) {
        var context = HiveAppContextHolder.getContext();
        return subscriptionService.previewChange(
                context.currentAccountId(), context.actorUserId(), request);
    }

    @PostMapping("/apply")
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionChangeApplyResponse apply(
            @Valid @RequestBody SubscriptionChangeApplyRequest request) {
        var context = HiveAppContextHolder.getContext();
        return subscriptionService.applyChange(
                context.currentAccountId(), context.actorUserId(), request);
    }

    @GetMapping("/changes")
    public List<SubscriptionChangeOperationDto> changes() {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        return subscriptionService.listChangeOperations(accountId);
    }

    @DeleteMapping("/changes/{operationId}")
    public SubscriptionChangeOperationDto cancelChange(@PathVariable UUID operationId) {
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        return subscriptionService.cancelPendingChange(accountId, operationId);
    }
}
