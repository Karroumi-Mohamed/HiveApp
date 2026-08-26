package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType;
import com.hiveapp.platform.client.plan.domain.constant.ProductPriceStatus;
import com.hiveapp.platform.client.plan.dto.CreateProductPriceRequest;
import com.hiveapp.platform.client.plan.dto.ProductPriceActivationPreview;
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
    Page<ProductPriceDto> list(String search, ProductPriceOwnerType ownerType, UUID ownerId,
                               ProductPriceStatus status, String currencyCode, BillingCycle billingCycle,
                               Pageable pageable);
    ProductPriceDto get(UUID priceId);
    Page<ProductPriceHistoryEntryDto> history(UUID priceId, Pageable pageable);
    ProductPriceDto createDraft(ProductPriceOwnerType ownerType, UUID ownerId,
                                CreateProductPriceRequest request);
    ProductPriceDto updateDraft(UUID priceId, UpdateProductPriceRequest request);
    ProductPriceActivationPreview previewActivation(UUID priceId);
    ProductPriceDto activate(UUID priceId, long version, String reason);
    ProductPriceDto pause(UUID priceId, long version, String reason);
    ProductPriceDto reactivate(UUID priceId, long version, String reason);
    ProductPriceDto revise(UUID priceId, long version, String reason);
    ProductPriceReplacementPreview previewReplacement(
            UUID successorPriceId, ProductPriceReplacementPreviewRequest request);
    ProductPriceReplacementResult scheduleReplacement(
            UUID successorPriceId, ProductPriceReplacementRequest request);
    ProductPriceDto archive(UUID priceId, long version, String reason);
    void deleteDraft(UUID priceId, long version);
}
