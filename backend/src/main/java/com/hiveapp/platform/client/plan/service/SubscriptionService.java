package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.dto.ClientPlanCatalogResponse;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeApplyResponse;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeApplyRequest;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangePreviewResponse;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeRequest;
import com.hiveapp.platform.client.plan.dto.QuotaPackageSelection;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeOperationDto;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.hiveapp.platform.client.plan.dto.SubscriptionDto;
import com.hiveapp.platform.client.plan.dto.ProductPriceSelectionRequest;

public interface SubscriptionService {
    /** Internal cross-service lookup; callers carry their own authorization. */
    Subscription getSubscription(UUID accountId);

    /**
     * Read model for the client's own subscription. Mapping happens inside this service's
     * transaction; the controller must not project the entity after the transaction closes,
     * which fails with open-in-view disabled.
     */
    SubscriptionDto getMySubscription(UUID accountId);
    ClientPlanCatalogResponse catalog(UUID accountId);
    /** Internal operator catalogue; its caller must carry the platform-admin authorization guard. */
    ClientPlanCatalogResponse catalogAsOperator(UUID accountId);
    SubscriptionChangePreviewResponse previewChange(
            UUID accountId, UUID actorUserId, SubscriptionChangeRequest request);
    /** Internal operator surface; its caller must carry the platform-admin authorization guard. */
    SubscriptionChangePreviewResponse previewChangeAsOperator(
            UUID accountId, UUID actorUserId, SubscriptionChangeRequest request);
    SubscriptionChangeApplyResponse applyChange(
            UUID accountId, UUID actorUserId, SubscriptionChangeApplyRequest request);
    /** Internal operator surface; its caller must carry the platform-admin authorization guard. */
    SubscriptionChangeApplyResponse applyChangeAsOperator(
            UUID accountId, UUID actorUserId, SubscriptionChangeApplyRequest request);
    List<SubscriptionChangeOperationDto> listChangeOperations(UUID accountId);
    /** Internal operator surface; its caller must carry the platform-admin authorization guard. */
    List<SubscriptionChangeOperationDto> listChangeOperationsAsOperator(UUID accountId);
    SubscriptionChangeOperationDto cancelPendingChange(UUID accountId, UUID operationId);
    /** Internal operator surface; its caller must carry the platform-admin authorization guard. */
    SubscriptionChangeOperationDto cancelPendingChangeAsOperator(UUID accountId, UUID operationId);
    Subscription createSubscription(UUID accountId, String planCode);
    Subscription createSubscription(
            UUID accountId, String planCode, ProductPriceSelectionRequest priceSelection);
    Subscription createTrial(UUID accountId, String planCode, int trialDays);
    Subscription createTrial(
            UUID accountId, String planCode, int trialDays, ProductPriceSelectionRequest priceSelection);
    Subscription updateOverrides(UUID accountId, Set<String> addOnCodes, List<QuotaPackageSelection> quotaPackages);
}
