package com.hiveapp.platform.client.plan.api;

import com.hiveapp.platform.client.plan.domain.constant.BillingCycle;
import com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType;
import com.hiveapp.platform.client.plan.domain.constant.ProductPriceStatus;
import com.hiveapp.platform.client.plan.dto.CreateProductPriceRequest;
import com.hiveapp.platform.client.plan.dto.ProductPriceActivationPreview;
import com.hiveapp.platform.client.plan.dto.ProductPriceActivationRequest;
import com.hiveapp.platform.client.plan.dto.ProductPriceDto;
import com.hiveapp.platform.client.plan.dto.ProductPriceHistoryEntryDto;
import com.hiveapp.platform.client.plan.dto.ProductPriceReplacementPreview;
import com.hiveapp.platform.client.plan.dto.ProductPriceReplacementPreviewRequest;
import com.hiveapp.platform.client.plan.dto.ProductPriceReplacementRequest;
import com.hiveapp.platform.client.plan.dto.ProductPriceReplacementResult;
import com.hiveapp.platform.client.plan.dto.ProductPriceVersionRequest;
import com.hiveapp.platform.client.plan.dto.UpdateProductPriceRequest;
import com.hiveapp.platform.client.plan.service.ProductPriceAdminService;
import com.hiveapp.shared.api.PageResponse;
import com.hiveapp.shared.exception.InvalidRequestException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/product-prices")
@RequiredArgsConstructor
public class ProductPriceAdminController {

    private static final Map<String, String> SORTABLE = Map.of(
            "createdAt", "createdAt",
            "updatedAt", "updatedAt",
            "effectiveFrom", "effectiveFrom",
            "amount", "amount",
            "currencyCode", "currencyCode",
            "billingCycle", "billingCycle",
            "status", "status",
            "revisionNumber", "revisionNumber");

    private final ProductPriceAdminService productPriceAdminService;

    @GetMapping
    public PageResponse<ProductPriceDto> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ProductPriceOwnerType ownerType,
            @RequestParam(required = false) UUID ownerId,
            @RequestParam(required = false) ProductPriceStatus status,
            @RequestParam(required = false) String currencyCode,
            @RequestParam(required = false) BillingCycle billingCycle,
            @RequestParam(required = false) java.util.Set<UUID> ownerIds,
            @RequestParam(defaultValue = "false") boolean currentOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        return PageResponse.from(productPriceAdminService.list(search, ownerType, ownerId, status,
                currencyCode, billingCycle, ownerIds, currentOnly, pageRequest(page, size, sort, direction)));
    }

    @GetMapping("/{priceId}")
    public ProductPriceDto get(@PathVariable UUID priceId) {
        return productPriceAdminService.get(priceId);
    }

    @GetMapping("/{priceId}/history")
    public PageResponse<ProductPriceHistoryEntryDto> history(
            @PathVariable UUID priceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new InvalidRequestException("Page must be non-negative and size must be between 1 and 100");
        }
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "occurredAt")
                        .and(Sort.by(Sort.Direction.DESC, "id")));
        return PageResponse.from(productPriceAdminService.history(priceId, pageable));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductPriceDto create(
            @RequestParam ProductPriceOwnerType ownerType,
            @RequestParam UUID ownerId,
            @Valid @RequestBody CreateProductPriceRequest request) {
        return productPriceAdminService.createDraft(ownerType, ownerId, request);
    }

    @PutMapping("/{priceId}")
    public ProductPriceDto update(@PathVariable UUID priceId,
                                  @Valid @RequestBody UpdateProductPriceRequest request) {
        return productPriceAdminService.updateDraft(priceId, request);
    }

    @GetMapping("/{priceId}/activation-preview")
    public ProductPriceActivationPreview activationPreview(@PathVariable UUID priceId) {
        return productPriceAdminService.previewActivation(priceId);
    }

    @PostMapping("/{priceId}/activate")
    public ProductPriceDto activate(@PathVariable UUID priceId,
                                    @Valid @RequestBody ProductPriceActivationRequest request) {
        return productPriceAdminService.activate(priceId, request);
    }

    @PostMapping("/{priceId}/pause")
    public ProductPriceDto pause(@PathVariable UUID priceId,
                                 @Valid @RequestBody ProductPriceVersionRequest request) {
        return productPriceAdminService.pause(priceId, request.version(), request.reason());
    }

    @PostMapping("/{priceId}/reactivate")
    public ProductPriceDto reactivate(@PathVariable UUID priceId,
                                      @Valid @RequestBody ProductPriceActivationRequest request) {
        return productPriceAdminService.reactivate(priceId, request);
    }

    @PostMapping("/{priceId}/revisions")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductPriceDto revise(@PathVariable UUID priceId,
                                  @Valid @RequestBody ProductPriceVersionRequest request) {
        return productPriceAdminService.revise(priceId, request.version(), request.reason());
    }

    @PostMapping("/{priceId}/replacement-preview")
    public ProductPriceReplacementPreview replacementPreview(
            @PathVariable UUID priceId,
            @Valid @RequestBody ProductPriceReplacementPreviewRequest request) {
        return productPriceAdminService.previewReplacement(priceId, request);
    }

    @PostMapping("/{priceId}/schedule-replacement")
    public ProductPriceReplacementResult scheduleReplacement(
            @PathVariable UUID priceId,
            @Valid @RequestBody ProductPriceReplacementRequest request) {
        return productPriceAdminService.scheduleReplacement(priceId, request);
    }

    @PostMapping("/{priceId}/archive")
    public ProductPriceDto archive(@PathVariable UUID priceId,
                                   @Valid @RequestBody ProductPriceVersionRequest request) {
        return productPriceAdminService.archive(priceId, request.version(), request.reason());
    }

    @DeleteMapping("/{priceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID priceId,
                       @RequestParam long version) {
        productPriceAdminService.deleteDraft(priceId, version);
    }

    private PageRequest pageRequest(int page, int size, String sort, String direction) {
        if (page < 0 || size < 1 || size > 100) {
            throw new InvalidRequestException("Page must be non-negative and size must be between 1 and 100");
        }
        if (direction != null
                && !"asc".equalsIgnoreCase(direction)
                && !"desc".equalsIgnoreCase(direction)) {
            throw new InvalidRequestException("Direction must be 'asc' or 'desc'.");
        }
        Sort.Direction resolvedDirection = "asc".equalsIgnoreCase(direction)
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        String property;
        if (sort == null || sort.isBlank()) {
            property = "createdAt";
        } else {
            property = SORTABLE.get(sort);
            if (property == null) {
                throw new InvalidRequestException("Unsupported sort column: " + sort);
            }
        }
        return PageRequest.of(page, size, Sort.by(resolvedDirection, property)
                .and(Sort.by(resolvedDirection, "id")));
    }
}
