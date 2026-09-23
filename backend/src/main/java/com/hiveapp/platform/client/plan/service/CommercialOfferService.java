package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.dto.*;
import java.util.*;
import org.springframework.data.domain.*;

public interface CommercialOfferService {
  Page<CommercialOfferViews.ClientOffer> catalogue(UUID accountId, Pageable p);

  CommercialOfferViews.ClientOffer detail(UUID accountId, UUID offerId);

  boolean isCatalogueAvailable(UUID accountId, UUID offerId);

  CommercialOfferViews.CodeResolution resolveCode(
      UUID accountId, UUID actor, CommercialOfferRequests.ResolveCode r);

  CommercialOfferViews.ClientEligibilityPreview preview(
      UUID accountId, UUID actor, UUID offerId, String discoveryToken);

  CommercialOfferViews.AccountEligibilityAssessment assessForOperator(
      UUID accountId, UUID actor, UUID offerId);

  CommercialOfferViews.ClientAcceptance acceptClient(
      UUID accountId,
      UUID actor,
      UUID offerId,
      String idempotencyKey,
      CommercialOfferRequests.ClientAccept request);

  CommercialOfferViews.AdminAcceptance acceptAsOperator(
      UUID accountId,
      UUID actor,
      UUID offerId,
      String idempotencyKey,
      CommercialOfferRequests.OperatorAccept request);

  Page<CommercialOfferViews.ClientRedemption> history(UUID accountId, Pageable p);

  CommercialOfferViews.ClientRedemption redemption(UUID accountId, UUID id);
}
