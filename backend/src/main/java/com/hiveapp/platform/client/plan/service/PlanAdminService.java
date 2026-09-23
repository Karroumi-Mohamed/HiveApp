package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.AddOnStatus;
import com.hiveapp.platform.client.plan.domain.constant.PlanExtensionPolicy;
import com.hiveapp.platform.client.plan.domain.constant.PlanStatus;
import com.hiveapp.platform.client.plan.domain.constant.ProductSalesVisibility;
import com.hiveapp.platform.client.plan.domain.constant.QuotaPackageStatus;
import com.hiveapp.platform.client.plan.dto.AddOnActivationPreviewDto;
import com.hiveapp.platform.client.plan.dto.AddOnChooserItemDto;
import com.hiveapp.platform.client.plan.dto.AddOnDto;
import com.hiveapp.platform.client.plan.dto.AddOnOperationalListItemDto;
import com.hiveapp.platform.client.plan.dto.AssignAddOnFeatureRequest;
import com.hiveapp.platform.client.plan.dto.AssignPlanFeatureRequest;
import com.hiveapp.platform.client.plan.dto.CommercialOverviewDto;
import com.hiveapp.platform.client.plan.dto.CreateAddOnRequest;
import com.hiveapp.platform.client.plan.dto.CreatePlanRequest;
import com.hiveapp.platform.client.plan.dto.CreateQuotaPackageRequest;
import com.hiveapp.platform.client.plan.dto.DeletePlanRequest;
import com.hiveapp.platform.client.plan.dto.PlanActivationPreviewDto;
import com.hiveapp.platform.client.plan.dto.PlanBranchRequest;
import com.hiveapp.platform.client.plan.dto.PlanChooserItemDto;
import com.hiveapp.platform.client.plan.dto.PlanDeletionPreview;
import com.hiveapp.platform.client.plan.dto.PlanDetailDto;
import com.hiveapp.platform.client.plan.dto.PlanDto;
import com.hiveapp.platform.client.plan.dto.PlanFeatureDto;
import com.hiveapp.platform.client.plan.dto.PlanOperationalListItemDto;
import com.hiveapp.platform.client.plan.dto.PlanSubscriberDto;
import com.hiveapp.platform.client.plan.dto.PlanSubscriberOwnerLookupDto;
import com.hiveapp.platform.client.plan.dto.QuotaPackageActivationPreviewDto;
import com.hiveapp.platform.client.plan.dto.QuotaPackageChooserItemDto;
import com.hiveapp.platform.client.plan.dto.QuotaPackageComparisonDto;
import com.hiveapp.platform.client.plan.dto.QuotaPackageDto;
import com.hiveapp.platform.client.plan.dto.QuotaPackageHistoryEntryDto;
import com.hiveapp.platform.client.plan.dto.QuotaPackageLifecycleRequest;
import com.hiveapp.platform.client.plan.dto.QuotaPackageOperationalListItemDto;
import com.hiveapp.platform.client.plan.dto.QuotaPackageRevisionRequest;
import com.hiveapp.platform.client.plan.dto.QuotaPackageRevisionResult;
import com.hiveapp.platform.client.plan.dto.UpdateAddOnRequest;
import com.hiveapp.platform.client.plan.dto.UpdatePlanRequest;
import com.hiveapp.platform.client.plan.dto.UpdateQuotaPackageRequest;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PlanAdminService {
  com.hiveapp.platform.client.plan.dto.PlanVersionRolloutModels.Detail createVersionRollout(
      UUID targetId, com.hiveapp.platform.client.plan.dto.PlanVersionRolloutModels.Request request);

  org.springframework.data.domain.Page<
          com.hiveapp.platform.client.plan.dto.PlanVersionRolloutModels.Summary>
      listVersionRollouts(UUID planId, org.springframework.data.domain.Pageable pageable);

  com.hiveapp.platform.client.plan.dto.PlanVersionRolloutModels.Detail getVersionRollout(
      UUID jobId);

  org.springframework.data.domain.Page<
          com.hiveapp.platform.client.plan.dto.PlanVersionRolloutModels.Item>
      versionRolloutResults(
          UUID jobId,
          com.hiveapp.platform.client.plan.domain.constant.SubscriptionChangeJobItemStatus status,
          String reason,
          org.springframework.data.domain.Pageable pageable);

  com.hiveapp.platform.client.plan.dto.PlanVersionRolloutModels.Detail confirmVersionRollout(
      UUID jobId, com.hiveapp.platform.client.plan.dto.PlanVersionRolloutModels.Confirm request);

  com.hiveapp.platform.client.plan.dto.PlanVersionRolloutModels.Detail cancelVersionRollout(
      UUID jobId, String reason);

  com.hiveapp.platform.client.plan.dto.PlanVersionRolloutModels.Detail retryVersionRollout(
      UUID jobId, String reason);

  java.util.List<com.hiveapp.platform.client.plan.dto.SubscriptionChangeJobModels.Identity>
      versionRolloutIdentities(UUID jobId, java.util.List<UUID> resultIds);

  com.hiveapp.platform.client.plan.dto.PlanVersionRolloutModels.Detail retryVersionNotices(
      UUID jobId, com.hiveapp.platform.client.plan.dto.PlanContentNoticeModels.Retry request);

  com.hiveapp.platform.client.plan.dto.PlanVersionApplicationModels.Preview
      previewVersionApplication(
          UUID targetId,
          UUID accountId,
          com.hiveapp.platform.client.plan.dto.PlanVersionApplicationModels.Request request);

  com.hiveapp.platform.client.plan.dto.PlanVersionApplicationModels.Result applyVersionNow(
      UUID targetId,
      UUID accountId,
      com.hiveapp.platform.client.plan.dto.PlanVersionApplicationModels.ApplyNow request);

  Page<com.hiveapp.platform.client.plan.dto.PlanVersionModels.Family> listPlanFamilies(
      String search, Pageable pageable);

  com.hiveapp.platform.client.plan.dto.PlanVersionModels.Versions listPlanVersions(
      UUID planId, Pageable pageable);

  com.hiveapp.platform.client.plan.dto.PlanVersionModels.Comparison comparePlanVersions(
      UUID sourceId, UUID targetId);

  com.hiveapp.platform.client.plan.dto.PlanVersionModels.CatalogComparison comparePlans(List<UUID> ids);

  com.hiveapp.platform.client.plan.dto.PlanVersionModels.Version selectPublicVersion(
      UUID planId, com.hiveapp.platform.client.plan.dto.PlanVersionModels.SelectPublic request);

  com.hiveapp.platform.client.plan.dto.PlanVersionModels.Version updatePlanMetadata(
      UUID planId, com.hiveapp.platform.client.plan.dto.PlanVersionModels.Metadata request);

  CommercialOverviewDto getCommercialOverview();

  Page<PlanOperationalListItemDto> listPlans(
      String search,
      PlanStatus status,
      ProductSalesVisibility salesVisibility,
      PlanExtensionPolicy extensionPolicy,
      UUID lineageId,
      Pageable pageable);

  PlanOperationalListItemDto getPlanOperations(UUID planId);

  Page<PlanChooserItemDto> choosePlans(
      String search, ProductSalesVisibility salesVisibility, Pageable pageable);

  List<PlanChooserItemDto> resolvePlanChoices(Collection<UUID> ids);

  List<PlanChooserItemDto> resolvePlanChoicesByCode(Collection<String> codes);

  PlanDetailDto getPlanDetail(UUID planId);

  PlanDto createPlan(CreatePlanRequest request);

  PlanDto duplicatePlan(UUID sourcePlanId, long expectedVersion, PlanBranchRequest request);

  PlanDto createPlanVersion(UUID sourcePlanId, long expectedVersion, PlanBranchRequest request);

  PlanDto updatePlan(UUID planId, UpdatePlanRequest request);

  default PlanDto transitionStatus(
      UUID planId, PlanStatus targetStatus, long expectedVersion, String reason) {
    return transitionStatus(planId, targetStatus, expectedVersion, reason, null);
  }

  PlanDto transitionStatus(
      UUID planId,
      PlanStatus targetStatus,
      long expectedVersion,
      String reason,
      String activationPreviewToken);

  PlanActivationPreviewDto previewPlanActivation(UUID planId);

  PlanDeletionPreview previewPlanDeletion(UUID planId);

  void deletePlan(UUID planId, DeletePlanRequest request);

  List<PlanFeatureDto> listPlanFeatures(UUID planId);

  Page<PlanSubscriberDto> listPlanSubscribers(
      UUID planId,
      String search,
      com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus status,
      Pageable pageable);

  Page<com.hiveapp.platform.client.plan.dto.PlanSubscriberViewModels.Subscriber> listFamilySubscribers(
      UUID planId, com.hiveapp.platform.client.plan.dto.PlanSubscriberViewModels.View view, String search,
      com.hiveapp.platform.client.plan.domain.constant.SubscriptionStatus status, String currency,
      com.hiveapp.platform.client.plan.domain.constant.BillingCycle cycle, Pageable pageable);

  Page<com.hiveapp.platform.client.plan.service.PlanVersionHistory.Event> planVersionHistory(UUID planId,
      com.hiveapp.platform.client.plan.service.PlanVersionHistory.Kind kind, UUID actorId,
      java.time.Instant from, java.time.Instant until, Pageable pageable);

  Page<PlanSubscriberOwnerLookupDto> findPlanSubscribersByOwnerEmail(
      UUID planId, String ownerEmail, Pageable pageable);

  PlanFeatureDto assignFeature(UUID planId, long expectedVersion, AssignPlanFeatureRequest request);

  PlanFeatureDto updateFeature(
      UUID planId, UUID planFeatureId, long expectedVersion, AssignPlanFeatureRequest request);

  void removeFeature(UUID planId, UUID planFeatureId, long expectedVersion);

  Page<AddOnOperationalListItemDto> listAddOns(
      String search,
      AddOnStatus status,
      ProductSalesVisibility salesVisibility,
      UUID lineageId,
      String featureCode,
      String targetPlanCode,
      Pageable pageable);

  AddOnOperationalListItemDto getAddOnOperations(UUID addOnId);

  Page<AddOnChooserItemDto> chooseAddOns(
      String search, ProductSalesVisibility salesVisibility, String featureCode, Pageable pageable);

  List<AddOnChooserItemDto> resolveAddOnChoices(Collection<UUID> ids);

  List<AddOnChooserItemDto> resolveAddOnChoicesByCode(Collection<String> codes);

  AddOnDto getAddOn(UUID addOnId);

  AddOnDto createAddOn(CreateAddOnRequest request);

  AddOnDto reviseAddOn(UUID sourceAddOnId, long expectedVersion);

  AddOnDto updateAddOn(UUID addOnId, UpdateAddOnRequest request);

  default AddOnDto transitionAddOnStatus(
      UUID addOnId, AddOnStatus targetStatus, long expectedVersion, String reason) {
    return transitionAddOnStatus(addOnId, targetStatus, expectedVersion, reason, null);
  }

  AddOnDto transitionAddOnStatus(
      UUID addOnId,
      AddOnStatus targetStatus,
      long expectedVersion,
      String reason,
      String activationPreviewToken);

  AddOnActivationPreviewDto previewAddOnActivation(UUID addOnId);

  void deleteAddOn(UUID addOnId, long expectedVersion);

  AddOnDto.FeatureItem assignAddOnFeature(
      UUID addOnId, long expectedVersion, AssignAddOnFeatureRequest request);

  AddOnDto.FeatureItem updateAddOnFeature(
      UUID addOnId, UUID addOnFeatureId, long expectedVersion, AssignAddOnFeatureRequest request);

  void removeAddOnFeature(UUID addOnId, UUID addOnFeatureId, long expectedVersion);

  Page<QuotaPackageOperationalListItemDto> listQuotaPackages(
      String search,
      QuotaPackageStatus status,
      ProductSalesVisibility salesVisibility,
      UUID lineageId,
      String featureCode,
      String resource,
      String targetPlanCode,
      String targetAddOnCode,
      Pageable pageable);

  QuotaPackageOperationalListItemDto getQuotaPackageOperations(UUID quotaPackageId);

  Page<QuotaPackageChooserItemDto> chooseQuotaPackages(
      String search,
      ProductSalesVisibility salesVisibility,
      String featureCode,
      String resource,
      Pageable pageable);

  List<QuotaPackageChooserItemDto> resolveQuotaPackageChoices(Collection<UUID> ids);

  List<QuotaPackageChooserItemDto> resolveQuotaPackageChoicesByCode(Collection<String> codes);

  QuotaPackageDto getQuotaPackage(UUID quotaPackageId);

  QuotaPackageDto createQuotaPackage(CreateQuotaPackageRequest request);

  QuotaPackageRevisionResult reviseQuotaPackage(
      UUID sourceQuotaPackageId, QuotaPackageRevisionRequest request);

  QuotaPackageComparisonDto compareQuotaPackage(UUID quotaPackageId, UUID againstQuotaPackageId);

  QuotaPackageActivationPreviewDto previewQuotaPackageActivation(UUID quotaPackageId);

  QuotaPackageDto changeQuotaPackageLifecycle(
      UUID quotaPackageId, QuotaPackageLifecycleRequest request);

  Page<QuotaPackageHistoryEntryDto> quotaPackageHistory(UUID quotaPackageId, Pageable pageable);

  QuotaPackageDto updateQuotaPackage(UUID quotaPackageId, UpdateQuotaPackageRequest request);

  void deleteQuotaPackage(UUID quotaPackageId, long expectedVersion);
}
