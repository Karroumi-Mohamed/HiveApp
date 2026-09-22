package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.account.dto.AccountBillingProfileModels;
import com.hiveapp.platform.client.plan.domain.constant.BillingTimelineEntryType;
import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.dto.BillingModels;
import com.hiveapp.platform.client.plan.dto.ClientPlanCatalogResponse;
import com.hiveapp.platform.client.plan.dto.CommercialOfferEffectSnapshot;
import com.hiveapp.platform.client.plan.dto.CommercialOfferRequests;
import com.hiveapp.platform.client.plan.dto.CommercialOfferViews;
import com.hiveapp.platform.client.plan.dto.SpecialAgreementModels;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeApplyRequest;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeApplyResponse;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeOperationDto;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangePreviewResponse;
import com.hiveapp.platform.client.plan.dto.SubscriptionChangeRequest;
import com.hiveapp.platform.client.plan.dto.SubscriptionDto;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SubscriptionService {
  /** Internal cross-service lookup; callers carry their own authorization. */
  Subscription getSubscription(UUID accountId);

  /**
   * Read model for the client's own subscription. Mapping happens inside this service's
   * transaction; the controller must not project the entity after the transaction closes, which
   * fails with open-in-view disabled.
   */
  SubscriptionDto getMySubscription(UUID accountId);

  Page<com.hiveapp.platform.client.plan.dto.RepricingModels.Notice> listMyPriceNotices(
      UUID accountId, UUID userId, Pageable pageable);

  void markPriceNoticeRead(UUID accountId, UUID userId, UUID noticeId);

  Page<com.hiveapp.platform.client.plan.dto.PlanContentNoticeModels.Notice> listMyContentNotices(
      UUID accountId, UUID userId, Pageable pageable);

  void markContentNoticeRead(UUID accountId, UUID userId, UUID noticeId);

  Page<SpecialAgreementModels.ClientView> listMySpecialAgreements(
      UUID accountId, Pageable pageable);

  ClientPlanCatalogResponse catalog(UUID accountId);

  /** Internal operator catalogue; its caller must carry the platform-admin authorization guard. */
  ClientPlanCatalogResponse catalogAsOperator(UUID accountId);

  SubscriptionChangePreviewResponse previewChange(
      UUID accountId, UUID actorUserId, SubscriptionChangeRequest request);

  /** Internal operator surface; its caller must carry the platform-admin authorization guard. */
  SubscriptionChangePreviewResponse previewChangeAsOperator(
      UUID accountId, UUID actorUserId, SubscriptionChangeRequest request);

  /** Client-authorized preview for an exact Offer selection, including DIRECT_ONLY products. */
  SubscriptionChangePreviewResponse previewOfferChange(
      UUID accountId,
      UUID actorUserId,
      SubscriptionChangeRequest request,
      List<CommercialOfferEffectSnapshot.QuotaBonus> quotaBonuses);

  /** Operator Offer preview; the calling admin surface must carry both platform permissions. */
  SubscriptionChangePreviewResponse previewOfferChangeAsOperator(
      UUID accountId,
      UUID actorUserId,
      SubscriptionChangeRequest request,
      List<CommercialOfferEffectSnapshot.QuotaBonus> quotaBonuses);

  SubscriptionChangeApplyResponse applyChange(
      UUID accountId, UUID actorUserId, SubscriptionChangeApplyRequest request);

  /** Permission-only gate used before an Offer reserves capacity for a client mutation. */
  void requireOfferApplyAuthority(UUID accountId, UUID actorUserId);

  /** Internal operator surface; its caller must carry the platform-admin authorization guard. */
  SubscriptionChangeApplyResponse applyChangeAsOperator(
      UUID accountId, UUID actorUserId, SubscriptionChangeApplyRequest request, String reason);

  SubscriptionChangeApplyResponse applyOfferChange(
      UUID accountId,
      UUID actorUserId,
      SubscriptionChangeApplyRequest request,
      String reason,
      UUID redemptionId,
      UUID applicationToken);

  /** Operator Offer apply; the calling admin surface must carry both platform permissions. */
  SubscriptionChangeApplyResponse applyOfferChangeAsOperator(
      UUID accountId,
      UUID actorUserId,
      SubscriptionChangeApplyRequest request,
      String reason,
      UUID redemptionId,
      UUID applicationToken);

  Page<SubscriptionChangeOperationDto> listChangeOperations(UUID accountId, Pageable pageable);

  SubscriptionChangeOperationDto cancelPendingChange(
      UUID accountId, UUID operationId, UUID actorUserId);

  SubscriptionChangeOperationDto cancelPendingChangeAsOperator(
      UUID accountId, UUID operationId, UUID actorUserId, String reason);

  Page<CommercialOfferViews.ClientOffer> offerCatalogue(UUID accountId, Pageable pageable);

  CommercialOfferViews.ClientOffer offerDetail(UUID accountId, UUID offerId);

  CommercialOfferViews.CodeResolution resolveOfferCode(
      UUID accountId, UUID actorUserId, CommercialOfferRequests.ResolveCode request);

  CommercialOfferViews.ClientEligibilityPreview previewOffer(
      UUID accountId, UUID actorUserId, UUID offerId, CommercialOfferRequests.Preview request);

  CommercialOfferViews.ClientAcceptance acceptOffer(
      UUID accountId,
      UUID actorUserId,
      UUID offerId,
      String idempotencyKey,
      CommercialOfferRequests.ClientAccept request);

  Page<CommercialOfferViews.ClientRedemption> offerHistory(UUID accountId, Pageable pageable);

  CommercialOfferViews.ClientRedemption offerRedemption(UUID accountId, UUID redemptionId);

  Page<BillingModels.InvoiceRow> invoiceHistory(UUID accountId, Pageable pageable);

  BillingModels.ClientInvoiceDetail invoice(UUID accountId, UUID invoiceId);

  BillingModels.InvoiceDocument invoiceDocument(UUID accountId, UUID invoiceId);

  Page<BillingModels.FinancialTimelineEntry> financialTimeline(
      UUID accountId,
      BillingTimelineEntryType type,
      String currencyCode,
      Instant occurredFrom,
      Instant occurredUntil,
      Pageable pageable);

  AccountBillingProfileModels.Profile billingProfile(UUID accountId);

  AccountBillingProfileModels.Profile updateBillingProfile(
      UUID accountId, AccountBillingProfileModels.UpdateRequest request);
}
