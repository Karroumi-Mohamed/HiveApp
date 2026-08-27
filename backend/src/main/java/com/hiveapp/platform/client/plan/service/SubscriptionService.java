package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.dto.ClientPlanCatalogResponse;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeApplyResponse;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeApplyRequest;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangePreviewResponse;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeRequest;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeOperationDto;
import com.hiveapp.platform.client.plan.dto.SubscriptionDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

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
            UUID accountId, UUID actorUserId, SubscriptionChangeApplyRequest request, String reason);
    Page<SubscriptionChangeOperationDto> listChangeOperations(UUID accountId, Pageable pageable);
    SubscriptionChangeOperationDto cancelPendingChange(
            UUID accountId, UUID operationId, UUID actorUserId);
    SubscriptionChangeOperationDto cancelPendingChangeAsOperator(
            UUID accountId, UUID operationId, UUID actorUserId, String reason);
}
