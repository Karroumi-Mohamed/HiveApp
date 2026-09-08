package com.hiveapp.platform.client.plan.api;

import com.hiveapp.platform.admin.service.AdminSubscriptionService;
import com.hiveapp.platform.client.plan.dto.RepricingModels.*;
import com.hiveapp.shared.api.PageResponse;
import com.hiveapp.shared.security.context.HiveAppContextHolder;
import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/subscription-repricing")
public class SubscriptionRepricingAdminController {
  private final AdminSubscriptionService service;

  @PostMapping("/preview")
  public Preview preview(@Valid @RequestBody Request request) {
    return service.previewRepricing(actor(), request);
  }

  @PostMapping("/{id}/confirm")
  public Detail confirm(@PathVariable UUID id, @Valid @RequestBody Confirm request) {
    return service.confirmRepricing(id, actor(), request);
  }

  @GetMapping
  public PageResponse<Summary> list(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    return PageResponse.from(service.listRepricing(page(page, size)));
  }

  @GetMapping("/{id}")
  public Detail get(@PathVariable UUID id) {
    return service.getRepricing(id);
  }

  @GetMapping("/{id}/results")
  public PageResponse<Item> results(
      @PathVariable UUID id,
      @RequestParam(required = false) State status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return PageResponse.from(service.repricingResults(id, status, page(page, size)));
  }

  @PostMapping("/{id}/identities")
  public List<Identity> identities(
      @PathVariable UUID id, @Valid @RequestBody IdentityRequest request) {
    return service.repricingIdentities(id, request.itemIds());
  }

  @PostMapping("/{id}/cancel")
  public Detail cancel(@PathVariable UUID id, @Valid @RequestBody Reason request) {
    return service.cancelRepricing(id, actor(), request.reason(), null);
  }

  @PostMapping("/{id}/results/{itemId}/cancel")
  public Detail cancelItem(
      @PathVariable UUID id, @PathVariable UUID itemId, @Valid @RequestBody Reason request) {
    return service.cancelRepricing(id, actor(), request.reason(), itemId);
  }

  @PostMapping("/{id}/retry-notices")
  public Detail retry(@PathVariable UUID id, @Valid @RequestBody Reason request) {
    return service.retryRepricing(id, request.reason());
  }

  @PostMapping("/{id}/results/{itemId}/retry")
  public Detail retryItem(
      @PathVariable UUID id, @PathVariable UUID itemId, @Valid @RequestBody Reason request) {
    return service.retryRepricingExecution(id, itemId, request.reason());
  }

  private UUID actor() {
    return HiveAppContextHolder.getContext().actorUserId();
  }

  private Pageable page(int page, int size) {
    return CommercialProductPageRequest.of(
        page,
        size,
        "createdAt",
        "desc",
        Map.of("createdAt", "createdAt"),
        "createdAt",
        Sort.Direction.DESC);
  }
}
