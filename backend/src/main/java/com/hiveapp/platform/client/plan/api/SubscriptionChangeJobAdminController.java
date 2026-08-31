package com.hiveapp.platform.client.plan.api;

import com.hiveapp.platform.admin.service.AdminSubscriptionService;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeJobItemStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeJobStatus;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeJobModels;
import com.hiveapp.shared.api.PageResponse;
import com.hiveapp.shared.security.HiveAppUserDetails;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Operational API for reviewed, durable changes across an immutable Account population. */
@RestController
@RequestMapping("/api/admin/subscription-change-jobs")
@RequiredArgsConstructor
public class SubscriptionChangeJobAdminController {

  private static final Map<String, String> JOB_SORTS =
      Map.of(
          "createdAt", "createdAt",
          "executeAt", "executeAt",
          "status", "status",
          "completedAt", "completedAt");
  private static final Map<String, String> RESULT_SORTS =
      Map.of(
          "createdAt", "createdAt",
          "status", "status",
          "lastAttemptAt", "lastAttemptAt",
          "completedAt", "completedAt");

  private final AdminSubscriptionService subscriptions;

  @PostMapping("/preview")
  @ResponseStatus(HttpStatus.CREATED)
  public SubscriptionChangeJobModels.Preview preview(
      @Valid @RequestBody SubscriptionChangeJobModels.PreviewRequest request,
      Authentication authentication) {
    return subscriptions.previewChangeJob(actorUserId(authentication), request);
  }

  @PostMapping("/{jobId}/confirm")
  public SubscriptionChangeJobModels.Detail confirm(
      @PathVariable UUID jobId,
      @Valid @RequestBody SubscriptionChangeJobModels.ConfirmRequest request,
      Authentication authentication) {
    return subscriptions.confirmChangeJob(jobId, actorUserId(authentication), request);
  }

  @GetMapping
  public PageResponse<SubscriptionChangeJobModels.Summary> list(
      @RequestParam(required = false) SubscriptionChangeJobStatus status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String sort,
      @RequestParam(required = false) String direction) {
    return PageResponse.from(
        subscriptions.listChangeJobs(
            status,
            CommercialProductPageRequest.of(
                page, size, sort, direction, JOB_SORTS, "createdAt", Sort.Direction.DESC)));
  }

  @GetMapping("/{jobId}")
  public SubscriptionChangeJobModels.Detail get(@PathVariable UUID jobId) {
    return subscriptions.getChangeJob(jobId);
  }

  @GetMapping("/{jobId}/results")
  public PageResponse<SubscriptionChangeJobModels.Item> results(
      @PathVariable UUID jobId,
      @RequestParam(required = false) SubscriptionChangeJobItemStatus status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String sort,
      @RequestParam(required = false) String direction) {
    return PageResponse.from(
        subscriptions.listChangeJobResults(
            jobId,
            status,
            CommercialProductPageRequest.of(
                page, size, sort, direction, RESULT_SORTS, "createdAt", Sort.Direction.ASC)));
  }

  @PostMapping("/{jobId}/results/identities")
  public java.util.List<SubscriptionChangeJobModels.Identity> identities(
      @PathVariable UUID jobId,
      @Valid @RequestBody SubscriptionChangeJobModels.IdentityRequest request) {
    return subscriptions.resolveChangeJobResultIdentities(jobId, request.resultIds());
  }

  @PostMapping("/{jobId}/cancel")
  public SubscriptionChangeJobModels.Detail cancel(
      @PathVariable UUID jobId,
      @Valid @RequestBody SubscriptionChangeJobModels.CancelRequest request,
      Authentication authentication) {
    return subscriptions.cancelChangeJob(jobId, actorUserId(authentication), request);
  }

  @PostMapping("/{jobId}/retry")
  public SubscriptionChangeJobModels.Detail retry(
      @PathVariable UUID jobId,
      @Valid @RequestBody SubscriptionChangeJobModels.RetryRequest request,
      Authentication authentication) {
    return subscriptions.retryChangeJob(jobId, actorUserId(authentication), request);
  }

  private UUID actorUserId(Authentication authentication) {
    return ((HiveAppUserDetails) authentication.getPrincipal()).getUserId();
  }
}
