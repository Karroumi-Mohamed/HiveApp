package com.hiveapp.platform.client.plan.service.impl;

import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPreviewKind;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeJobItemStatus;
import com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeJobStatus;
import com.hiveapp.platform.client.plan.domain.entity.SubscriptionChangeJob;
import com.hiveapp.platform.client.plan.domain.entity.SubscriptionChangeJobItem;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionChangeJobItemRepository;
import com.hiveapp.platform.client.plan.domain.repository.SubscriptionChangeJobRepository;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeJobModels;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangePreviewResponse;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeRequest;
import com.hiveapp.platform.client.plan.dto.QuotaPackageSelection;
import com.hiveapp.platform.client.plan.service.CommercialCatalogVersionService;
import com.hiveapp.platform.client.plan.service.CommercialPreviewTokenService;
import com.hiveapp.platform.client.plan.service.SubscriptionChangeJobService;
import com.hiveapp.platform.client.plan.service.SubscriptionChangeJobAssessmentService;
import com.hiveapp.platform.registry.service.RegistryCatalogVersionService;
import com.hiveapp.shared.exception.InvalidRequestException;
import com.hiveapp.shared.exception.InvalidStateException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import com.hiveapp.shared.exception.StaleResourceVersionException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SubscriptionChangeJobServiceImpl implements SubscriptionChangeJobService {

  static final int MAX_TARGETS = 500;
  static final int PREVIEW_SAMPLE_SIZE = 25;

  private final SubscriptionChangeJobRepository jobs;
  private final SubscriptionChangeJobItemRepository items;
  private final AccountRepository accounts;
  private final SubscriptionChangeJobAssessmentService assessments;
  private final CommercialCatalogVersionService catalogVersions;
  private final RegistryCatalogVersionService registryVersions;
  private final CommercialPreviewTokenService evidence;
  private final Clock clock;

  @Override
  @Transactional
  public SubscriptionChangeJobModels.Preview preview(
      UUID actorUserId, SubscriptionChangeJobModels.PreviewRequest request) {
    LinkedHashSet<UUID> targetIds = new LinkedHashSet<>(request.accountIds());
    if (targetIds.isEmpty() || targetIds.size() != request.accountIds().size()) {
      throw new InvalidRequestException("Selected Accounts must be non-empty and unique.");
    }
    if (targetIds.size() > MAX_TARGETS) {
      throw new InvalidRequestException("A reviewed subscription job is limited to 500 Accounts.");
    }
    String reason = normalizeReason(request.reason());
    Instant now = clock.instant();
    Instant executeAt = request.executeAt() == null ? now : request.executeAt();
    if (executeAt.isBefore(now)) {
      throw new InvalidRequestException("Job execution time cannot be in the past.");
    }

    Map<UUID, Account> targetAccounts = new LinkedHashMap<>();
    accounts.findAllById(targetIds).stream()
        .sorted(Comparator.comparing(Account::getId))
        .forEach(account -> targetAccounts.put(account.getId(), account));
    if (targetAccounts.size() != targetIds.size()) {
      throw new InvalidRequestException("One or more selected Accounts are unavailable.");
    }

    long catalogRevision = catalogVersions.currentRevision();
    String registryVersion = registryVersions.currentVersion();
    SubscriptionChangeJob job = SubscriptionChangeJob.previewed(
        request.selection(), actorUserId, reason, executeAt,
        catalogRevision, registryVersion, now);

    for (Account account : targetAccounts.values()) {
      try {
        SubscriptionChangePreviewResponse preview = assessments.preview(
            account.getId(), actorUserId, request.selection());
        if (preview.catalogRevision() != catalogRevision
            || !preview.registryVersion().equals(registryVersion)) {
          throw new StaleResourceVersionException("Commercial state changed during job review.");
        }
        SubscriptionChangeJobModels.Assessment assessment = assessment(preview);
        SubscriptionChangeJobItemStatus itemStatus = preview.conflicts().isEmpty()
            ? SubscriptionChangeJobItemStatus.READY
            : SubscriptionChangeJobItemStatus.CONFLICT;
        job.addItem(account, assessment, itemStatus,
            itemStatus == SubscriptionChangeJobItemStatus.READY ? null : "PREVIEW_CONFLICT");
      } catch (StaleResourceVersionException exception) {
        throw exception;
      } catch (InvalidRequestException | InvalidStateException | ResourceNotFoundException exception) {
        job.addItem(
            account,
            unavailableAssessment(request.selection()),
            SubscriptionChangeJobItemStatus.CONFLICT,
            safeConflictCode(exception));
      }
    }
    catalogVersions.requireCurrent(catalogRevision);
    registryVersions.requireCurrent(registryVersion);
    String fingerprint = fingerprint(job, job.getItems());
    job.sealAssessment(fingerprint);
    SubscriptionChangeJob saved = jobs.saveAndFlush(job);
    CommercialPreviewTokenService.IssuedEvidence token = evidence.issue(
        CommercialPreviewKind.SUBSCRIPTION_CHANGE_JOB,
        saved.getId(), saved.getVersion(), actorUserId,
        catalogRevision, registryVersion, fingerprint, now);
    return toPreview(saved, token);
  }

  @Override
  @Transactional
  public SubscriptionChangeJobModels.Detail confirm(
      UUID jobId, UUID actorUserId, SubscriptionChangeJobModels.ConfirmRequest request) {
    SubscriptionChangeJob job = lock(jobId);
    catalogVersions.requireCurrent(job.getCatalogRevision());
    registryVersions.requireCurrent(job.getRegistryVersion());
    evidence.requireValid(
        request.previewToken(),
        CommercialPreviewKind.SUBSCRIPTION_CHANGE_JOB,
        job.getId(), job.getVersion(), actorUserId,
        job.getCatalogRevision(), job.getRegistryVersion(),
        fingerprint(job, job.getItems()),
        () -> new StaleResourceVersionException(
            "Subscription job review is stale. Review the population again."));
    if (!job.getRequestedByUserId().equals(actorUserId)) {
      throw new StaleResourceVersionException(
          "Subscription job review is stale. Review the population again.");
    }
    job.confirm(clock.instant());
    return toDetail(jobs.saveAndFlush(job));
  }

  @Override
  @Transactional(readOnly = true)
  public Page<SubscriptionChangeJobModels.Summary> list(
      SubscriptionChangeJobStatus status, Pageable pageable) {
    Page<SubscriptionChangeJob> result = status == null
        ? jobs.findAllByPlanLineageIdIsNull(pageable)
        : jobs.findAllByPlanLineageIdIsNullAndStatus(status, pageable);
    Map<UUID, EnumMap<SubscriptionChangeJobItemStatus, Integer>> counts =
        statusCounts(result.getContent().stream().map(SubscriptionChangeJob::getId).toList());
    return result.map(job -> toSummary(job, counts.getOrDefault(job.getId(), emptyCounts())));
  }

  @Override
  @Transactional(readOnly = true)
  public SubscriptionChangeJobModels.Detail get(UUID jobId) {
    return toDetail(find(jobId));
  }

  @Override
  @Transactional(readOnly = true)
  public Page<SubscriptionChangeJobModels.Item> results(
      UUID jobId, SubscriptionChangeJobItemStatus status, Pageable pageable) {
    requireExists(jobId);
    Page<SubscriptionChangeJobItem> result = status == null
        ? items.findAllByJobId(jobId, pageable)
        : items.findAll(
            (root, query, cb) -> cb.and(
                cb.equal(root.get("job").get("id"), jobId),
                cb.equal(root.get("status"), status)),
            pageable);
    return result.map(this::toItem);
  }

  @Override
  @Transactional(readOnly = true)
  public List<SubscriptionChangeJobModels.Identity> resolveIdentities(
      UUID jobId, Collection<UUID> itemIds) {
    requireExists(jobId);
    if (itemIds == null || itemIds.isEmpty() || itemIds.size() > 100) {
      throw new InvalidRequestException("Between 1 and 100 result identities are required.");
    }
    LinkedHashSet<UUID> unique = new LinkedHashSet<>(itemIds);
    if (unique.size() != itemIds.size()) {
      throw new InvalidRequestException("Result identity IDs must be unique.");
    }
    List<SubscriptionChangeJobItem> resolved = items.findAllByJobIdAndIdIn(jobId, unique);
    if (resolved.size() != unique.size()) {
      throw new InvalidRequestException("One or more subscription job results are unavailable.");
    }
    return resolved.stream()
        .sorted(Comparator.comparing(SubscriptionChangeJobItem::getId))
        .map(item -> new SubscriptionChangeJobModels.Identity(
            item.getId(), item.getAccount().getId(), item.getAccount().getName()))
        .toList();
  }

  @Override
  @Transactional
  public SubscriptionChangeJobModels.Detail cancel(
      UUID jobId, UUID actorUserId, SubscriptionChangeJobModels.CancelRequest request) {
    SubscriptionChangeJob job = lock(jobId);
    try {
      job.cancel(actorUserId, normalizeReason(request.reason()), clock.instant());
    } catch (IllegalStateException exception) {
      throw new InvalidStateException(exception.getMessage());
    }
    return toDetail(jobs.saveAndFlush(job));
  }

  @Override
  @Transactional
  public SubscriptionChangeJobModels.Detail retry(
      UUID jobId, UUID actorUserId, SubscriptionChangeJobModels.RetryRequest request) {
    SubscriptionChangeJob job = lock(jobId);
    try {
      job.prepareRetry(actorUserId, normalizeReason(request.reason()), clock.instant());
    } catch (IllegalStateException exception) {
      throw new InvalidStateException(exception.getMessage());
    }
    return toDetail(jobs.saveAndFlush(job));
  }

  private SubscriptionChangeJobModels.Assessment assessment(
      SubscriptionChangePreviewResponse preview) {
    return new SubscriptionChangeJobModels.Assessment(
        preview.subscriptionId(), preview.expectedSubscriptionVersion(),
        preview.currentPlanCode(), preview.targetPlanCode(),
        preview.currentPrice(), preview.previewPrice(), preview.currencyCode(),
        preview.timing(), preview.effectiveAt(), preview.conflicts());
  }

  private SubscriptionChangeJobModels.Assessment unavailableAssessment(
      SubscriptionChangeRequest selection) {
    return new SubscriptionChangeJobModels.Assessment(
        null, -1, null, selection.targetPlanCode(), null, null, null,
        selection.effectiveTiming(), null, List.of());
  }

  private SubscriptionChangeJobModels.Preview toPreview(
      SubscriptionChangeJob job,
      CommercialPreviewTokenService.IssuedEvidence token) {
    int ready = (int) job.getItems().stream()
        .filter(item -> item.getStatus() == SubscriptionChangeJobItemStatus.READY)
        .count();
    int conflicts = job.getItems().size() - ready;
    List<SubscriptionChangeJobModels.Item> sample = job.getItems().stream()
        .sorted(Comparator.comparing(item -> item.getAccount().getId()))
        .limit(PREVIEW_SAMPLE_SIZE)
        .map(this::toItem)
        .toList();
    return new SubscriptionChangeJobModels.Preview(
        job.getId(), job.getVersion(), job.getStatus(), job.getItems().size(),
        ready, conflicts, job.getExecuteAt(), token.evaluatedAt(), token.expiresAt(),
        token.token(), sample);
  }

  private SubscriptionChangeJobModels.Detail toDetail(SubscriptionChangeJob job) {
    return new SubscriptionChangeJobModels.Detail(toSummary(job), job.getSelection());
  }

  private SubscriptionChangeJobModels.Summary toSummary(SubscriptionChangeJob job) {
    EnumMap<SubscriptionChangeJobItemStatus, Integer> counts = emptyCounts();
    job.getItems().forEach(item -> counts.merge(item.getStatus(), 1, Integer::sum));
    return toSummary(job, counts);
  }

  private SubscriptionChangeJobModels.Summary toSummary(
      SubscriptionChangeJob job,
      Map<SubscriptionChangeJobItemStatus, Integer> counts) {
    return new SubscriptionChangeJobModels.Summary(
        job.getId(), job.getStatus(), counts.values().stream().mapToInt(Integer::intValue).sum(),
        counts.getOrDefault(SubscriptionChangeJobItemStatus.READY, 0),
        counts.getOrDefault(SubscriptionChangeJobItemStatus.APPLIED, 0),
        counts.getOrDefault(SubscriptionChangeJobItemStatus.PENDING_RENEWAL, 0),
        counts.getOrDefault(SubscriptionChangeJobItemStatus.AWAITING_PAYMENT, 0),
        counts.getOrDefault(SubscriptionChangeJobItemStatus.CONFLICT, 0),
        counts.getOrDefault(SubscriptionChangeJobItemStatus.FAILED, 0),
        counts.getOrDefault(SubscriptionChangeJobItemStatus.CANCELLED, 0),
        job.getExecuteAt(), job.getStartedAt(), job.getCompletedAt(),
        job.getRequestedByUserId(), job.getReason(), job.getRetryCount(),
        job.getLastRetriedByUserId(), job.getLastRetriedAt(), job.getLastRetryReason(),
        job.getVersion(), job.getCreatedAt());
  }

  private Map<UUID, EnumMap<SubscriptionChangeJobItemStatus, Integer>> statusCounts(
      Collection<UUID> jobIds) {
    Map<UUID, EnumMap<SubscriptionChangeJobItemStatus, Integer>> result = new LinkedHashMap<>();
    if (jobIds.isEmpty()) return result;
    items.countStatusesByJobIds(jobIds).forEach(row ->
        result.computeIfAbsent(row.getJobId(), ignored -> emptyCounts())
            .put(row.getStatus(), Math.toIntExact(row.getTotal())));
    return result;
  }

  private EnumMap<SubscriptionChangeJobItemStatus, Integer> emptyCounts() {
    return new EnumMap<>(SubscriptionChangeJobItemStatus.class);
  }

  private SubscriptionChangeJobModels.Item toItem(SubscriptionChangeJobItem item) {
    return new SubscriptionChangeJobModels.Item(
        item.getId(), item.getStatus(), item.getAssessment(),
        item.getSubscriptionOperationId(), item.getOutcomeCode(), item.getAttempts(),
        item.getLastAttemptAt(), item.getCompletedAt());
  }

  private SubscriptionChangeJob find(UUID jobId) {
    return jobs.findById(jobId).filter(job -> !job.isContentVersion())
        .orElseThrow(() -> new ResourceNotFoundException("SubscriptionChangeJob", "id", jobId));
  }

  private SubscriptionChangeJob lock(UUID jobId) {
    return jobs.lockById(jobId).filter(job -> !job.isContentVersion())
        .orElseThrow(() -> new ResourceNotFoundException("SubscriptionChangeJob", "id", jobId));
  }

  private void requireExists(UUID jobId) {
    if (!jobs.existsByIdAndPlanLineageIdIsNull(jobId)) {
      throw new ResourceNotFoundException("SubscriptionChangeJob", "id", jobId);
    }
  }

  private String normalizeReason(String value) {
    if (value == null || value.isBlank()) throw new InvalidRequestException("Reason is required.");
    String normalized = value.trim().replaceAll("\\s+", " ");
    if (normalized.length() > 2000) throw new InvalidRequestException("Reason is too long.");
    return normalized;
  }

  private String safeConflictCode(RuntimeException exception) {
    if (exception instanceof ResourceNotFoundException) return "SUBSCRIPTION_UNAVAILABLE";
    if (exception instanceof InvalidRequestException) return "SELECTION_INVALID";
    return "SUBSCRIPTION_STATE_CONFLICT";
  }

  private String fingerprint(
      SubscriptionChangeJob job, List<SubscriptionChangeJobItem> jobItems) {
    StringBuilder state = new StringBuilder();
    append(state, "subscription-change-job:v1");
    append(state, job.getRequestedByUserId());
    append(state, job.getReason());
    append(state, job.getExecuteAt());
    append(state, job.getCatalogRevision());
    append(state, job.getRegistryVersion());
    appendSelection(state, job.getSelection());
    jobItems.stream()
        .sorted(Comparator.comparing(item -> item.getAccount().getId()))
        .forEach(item -> {
          append(state, item.getAccount().getId());
          append(state, item.getStatus());
          append(state, item.getOutcomeCode());
          append(state, item.getAssessment());
        });
    return sha256(state.toString());
  }

  private void appendSelection(StringBuilder state, SubscriptionChangeRequest selection) {
    append(state, selection.targetPlanCode());
    append(state, selection.effectiveTiming());
    append(state, selection.planPriceSelection());
    if (selection.addOnCodes() != null) {
      selection.addOnCodes().stream().sorted().forEach(value -> append(state, "add-on:" + value));
    }
    if (selection.quotaPackages() != null) {
      selection.quotaPackages().stream()
          .sorted(Comparator.comparing(QuotaPackageSelection::packageCode))
          .forEach(value -> append(state, "package:" + value));
    }
    selection.addOnPriceEntryIds().entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(entry -> append(state, "add-on-price:" + entry));
    selection.quotaPackagePriceEntryIds().entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(entry -> append(state, "package-price:" + entry));
  }

  private void append(StringBuilder state, Object value) {
    String encoded = value == null ? "<null>" : value.toString();
    state.append(encoded.length()).append(':').append(encoded).append(';');
  }

  private String sha256(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }
}
