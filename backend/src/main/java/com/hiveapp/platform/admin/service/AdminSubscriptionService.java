package com.hiveapp.platform.admin.service;

import com.hiveapp.platform.admin.dto.AdminSubscriptionDto;
import com.hiveapp.platform.admin.dto.AdminSubscriptionChangeApplyRequest;
import com.hiveapp.platform.admin.dto.AdminSubscriptionChangeOperationDto;
import com.hiveapp.platform.admin.dto.SubscriptionAccountOwnerLookupDto;
import com.hiveapp.platform.admin.dto.SubscriptionAccountOperationalListItemDto;
import com.hiveapp.platform.client.account.dto.AccountDirectoryEntryDto;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeOperationDto;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeApplyResponse;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangePreviewResponse;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeRequest;
import com.hiveapp.platform.client.plan.dto.SubscriptionCheckoutDto;
import com.hiveapp.platform.client.plan.dto.AssignablePlanPriceDto;
import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus;
import com.hiveapp.platform.client.plan.dto.SubscriptionOverrideChoicePage;
import com.hiveapp.platform.client.plan.dto.SubscriptionAddOnOverrideChoiceDto;
import com.hiveapp.platform.client.plan.dto.SubscriptionQuotaPackageOverrideChoiceDto;
import com.hiveapp.platform.client.plan.dto.ClientPlanCatalogResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Operator-facing subscription contract. Every method returns a read model rather than a
 * persistence entity, so response composition and its transaction boundary stay inside the
 * service instead of being completed by the controller.
 */
public interface AdminSubscriptionService {
    Page<SubscriptionAccountOperationalListItemDto> searchAccounts(
            String query,
            Boolean accountActive,
            SubscriptionStatus subscriptionStatus,
            Boolean hasSubscription,
            String planCode,
            Pageable pageable);
    Page<SubscriptionAccountOwnerLookupDto> findAccountsByOwnerEmail(
            String ownerEmail,
            Boolean accountActive,
            SubscriptionStatus subscriptionStatus,
            Boolean hasSubscription,
            String planCode,
            Pageable pageable);
    Page<AccountDirectoryEntryDto> chooseAccounts(
            String query, Boolean active, Pageable pageable);
    List<AccountDirectoryEntryDto> resolveAccountChoices(Collection<UUID> ids);
    Page<AssignablePlanPriceDto> listAssignablePlanPrices(
            String search, String currencyCode, BillingCycle billingCycle, Pageable pageable);
    SubscriptionOverrideChoicePage<SubscriptionAddOnOverrideChoiceDto> chooseAddOnOverrides(
            UUID accountId, String search, Collection<String> selectedAddOnCodes,
            Pageable pageable);
    SubscriptionOverrideChoicePage<SubscriptionQuotaPackageOverrideChoiceDto>
            chooseQuotaPackageOverrides(
                    UUID accountId, String search, String featureCode, String resource,
                    Collection<String> selectedAddOnCodes, Pageable pageable);
    AdminSubscriptionDto getSubscription(UUID accountId);
    ClientPlanCatalogResponse changeCatalog(UUID accountId);
    Page<AdminSubscriptionChangeOperationDto> listChangeOperations(
            UUID accountId, Pageable pageable);
    SubscriptionChangePreviewResponse previewChange(
            UUID accountId, UUID actorUserId, SubscriptionChangeRequest request);
    SubscriptionChangeApplyResponse applyChange(
            UUID accountId, UUID actorUserId, AdminSubscriptionChangeApplyRequest request);
    SubscriptionChangeOperationDto cancelChange(
            UUID accountId, UUID operationId, UUID actorUserId, String reason);
    SubscriptionCheckoutDto confirmCheckoutManually(
            UUID checkoutId, UUID actorUserId, String reference, String reason);
}
