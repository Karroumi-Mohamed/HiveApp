package com.hiveapp.platform.client.plan.api;

import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeJobItemStatus;
import com.hiveapp.platform.client.plan.dto.PlanVersionRolloutModels.*;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeJobModels;
import com.hiveapp.platform.client.plan.service.PlanAdminService;
import com.hiveapp.shared.api.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/plan-version-applications")
@RequiredArgsConstructor
public class PlanVersionRolloutAdminController {
  private final PlanAdminService plans;

  @PostMapping
  public Detail create(@RequestParam UUID targetPlanId, @Valid @RequestBody Request request) {
    return plans.createVersionRollout(targetPlanId, request);
  }

  @GetMapping
  public PageResponse<Summary> list(
      @RequestParam UUID planId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return PageResponse.from(plans.listVersionRollouts(planId, page(page, size)));
  }

  @GetMapping("/{id}")
  public Detail get(@PathVariable UUID id) {
    return plans.getVersionRollout(id);
  }

  @GetMapping("/{id}/results")
  public PageResponse<Item> results(
      @PathVariable UUID id,
      @RequestParam(required = false) SubscriptionChangeJobItemStatus status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return PageResponse.from(plans.versionRolloutResults(id, status, page(page, size)));
  }

  @PostMapping("/{id}/confirm")
  public Detail confirm(@PathVariable UUID id, @Valid @RequestBody Confirm request) {
    return plans.confirmVersionRollout(id, request);
  }

  @PostMapping("/{id}/identities")
  public java.util.List<SubscriptionChangeJobModels.Identity> identities(
      @PathVariable UUID id,
      @Valid @RequestBody SubscriptionChangeJobModels.IdentityRequest request) {
    return plans.versionRolloutIdentities(id, request.resultIds());
  }

  @PostMapping("/{id}/cancel")
  public Detail cancel(
      @PathVariable UUID id,
      @Valid @RequestBody SubscriptionChangeJobModels.CancelRequest request) {
    return plans.cancelVersionRollout(id, request.reason());
  }

  @PostMapping("/{id}/retry")
  public Detail retry(
      @PathVariable UUID id, @Valid @RequestBody SubscriptionChangeJobModels.RetryRequest request) {
    return plans.retryVersionRollout(id, request.reason());
  }

  @PostMapping("/{id}/notices/retry")
  public Detail retryNotices(
      @PathVariable UUID id,
      @Valid @RequestBody
          com.hiveapp.platform.client.plan.dto.PlanContentNoticeModels.Retry request) {
    return plans.retryVersionNotices(id, request);
  }

  private PageRequest page(int page, int size) {
    if (page < 0 || size < 1 || size > 100)
      throw new com.hiveapp.shared.exception.InvalidRequestException(
          "Page must be non-negative and size between 1 and 100.");
    return PageRequest.of(page, size);
  }
}
