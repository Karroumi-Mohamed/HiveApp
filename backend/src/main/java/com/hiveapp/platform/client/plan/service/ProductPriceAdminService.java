package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType;
import com.hiveapp.platform.client.plan.domain.constant.ProductPriceStatus;
import com.hiveapp.platform.client.plan.dto.CreateProductPriceRequest;
import com.hiveapp.platform.client.plan.dto.ProductPriceActivationPreview;
import com.hiveapp.platform.client.plan.dto.ProductPriceActivationRequest;
import com.hiveapp.platform.client.plan.dto.ProductPriceChangeConfirmation;
import com.hiveapp.platform.client.plan.dto.ProductPriceChangePreview;
import com.hiveapp.platform.client.plan.dto.ProductPriceChangeRequest;
import com.hiveapp.platform.client.plan.dto.ProductPriceChangeResult;
import com.hiveapp.platform.client.plan.dto.ProductPriceDto;
import com.hiveapp.platform.client.plan.dto.ProductPriceHistoryEntryDto;
import com.hiveapp.platform.client.plan.dto.ProductPriceReplacementPreview;
import com.hiveapp.platform.client.plan.dto.ProductPriceReplacementPreviewRequest;
import com.hiveapp.platform.client.plan.dto.ProductPriceReplacementRequest;
import com.hiveapp.platform.client.plan.dto.ProductPriceReplacementResult;
import com.hiveapp.platform.client.plan.dto.UpdateProductPriceRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ProductPriceAdminService {
    ProductPriceChangePreview previewChange(UUID currentPriceId, ProductPriceChangeRequest request);
    ProductPriceChangeResult changePrice(UUID currentPriceId, ProductPriceChangeConfirmation request);
    ProductPriceChangeResult rescheduleChange(UUID currentPriceId, ProductPriceChangeConfirmation request);
    ProductPriceChangeResult cancelChange(UUID currentPriceId, ProductPriceChangeConfirmation request);
    Page<ProductPriceDto> list(String search, ProductPriceOwnerType ownerType, UUID ownerId,
                               ProductPriceStatus status, String currencyCode, BillingCycle billingCycle,
                               java.util.Set<UUID> ownerIds, boolean currentOnly, UUID sourcePriceId, Pageable pageable);
    ProductPriceDto get(UUID priceId);
    Page<ProductPriceHistoryEntryDto> history(UUID priceId, Pageable pageable);
    ProductPriceDto createDraft(ProductPriceOwnerType ownerType, UUID ownerId,
                                CreateProductPriceRequest request);
    ProductPriceDto updateDraft(UUID priceId, UpdateProductPriceRequest request);
    ProductPriceActivationPreview previewActivation(UUID priceId);
    ProductPriceDto activate(UUID priceId, ProductPriceActivationRequest request);
    ProductPriceDto pause(UUID priceId, long version, String reason);
    ProductPriceDto reactivate(UUID priceId, ProductPriceActivationRequest request);
    ProductPriceDto revise(UUID priceId, long version, String reason);
    ProductPriceReplacementPreview previewReplacement(
            UUID successorPriceId, ProductPriceReplacementPreviewRequest request);
    ProductPriceReplacementResult scheduleReplacement(
            UUID successorPriceId, ProductPriceReplacementRequest request);
    ProductPriceDto archive(UUID priceId, long version, String reason);
    void deleteDraft(UUID priceId, long version);
}
