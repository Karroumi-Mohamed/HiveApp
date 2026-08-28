package com.hiveapp.platform.client.plan.service.impl;

import com.hiveapp.platform.client.plan.domain.constant.CommercialOfferBlocker;
import com.hiveapp.platform.client.plan.domain.constant.CommercialOfferDefinitionIssueCode;
import com.hiveapp.platform.client.plan.domain.constant.CommercialOfferStatus;
import com.hiveapp.platform.client.plan.domain.entity.CommercialOffer;
import com.hiveapp.platform.client.plan.domain.repository.CommercialOfferRepository;
import com.hiveapp.platform.client.plan.dto.CommercialOfferViews;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Typed publication state backed by the same definition assessment used by authoring. */
@Component
@RequiredArgsConstructor
class CommercialOfferPublicationValidator {
  private final CommercialOfferRepository offers;
  private final CommercialOfferDefinitionAssessor definitionAssessor;

  List<CommercialOfferBlocker> blockers(CommercialOffer offer) {
    List<CommercialOfferBlocker> blockers = new ArrayList<>();
    if (offer.getStatus() != CommercialOfferStatus.DRAFT
        && offer.getStatus() != CommercialOfferStatus.RETIRED) {
      blockers.add(CommercialOfferBlocker.INVALID_LIFECYCLE_STATE);
    }
    if (offer.getStatus() == CommercialOfferStatus.RETIRED
        && offers
            .findFirstByLineage_IdAndStatus(offer.getLineageId(), CommercialOfferStatus.PUBLISHED)
            .isPresent()) {
      blockers.add(CommercialOfferBlocker.PUBLISHED_SUCCESSOR_EXISTS);
    }
    blockers.addAll(definitionBlockers(definitionIssues(offer)));
    return List.copyOf(new LinkedHashSet<>(blockers));
  }

  List<CommercialOfferViews.DefinitionIssue> definitionIssues(CommercialOffer offer) {
    return definitionAssessor.assessOffer(offer).issues();
  }

  List<CommercialOfferBlocker> actionBlockers(
      CommercialOffer offer, boolean publishedSuccessorExists) {
    List<CommercialOfferBlocker> blockers =
        new ArrayList<>(definitionBlockers(definitionIssues(offer)));
    if (offer.getStatus() == CommercialOfferStatus.RETIRED && publishedSuccessorExists) {
      blockers.add(CommercialOfferBlocker.PUBLISHED_SUCCESSOR_EXISTS);
    }
    return List.copyOf(new LinkedHashSet<>(blockers));
  }

  boolean hasInvalidSelection(CommercialOffer offer) {
    return !definitionIssues(offer).isEmpty();
  }

  void validateSelection(CommercialOffer offer) {
    definitionAssessor.requireValid(definitionAssessor.assessOffer(offer));
  }

  private List<CommercialOfferBlocker> definitionBlockers(
      List<CommercialOfferViews.DefinitionIssue> issues) {
    List<CommercialOfferBlocker> result = new ArrayList<>();
    for (var issue : issues) {
      CommercialOfferDefinitionIssueCode code = issue.code();
      switch (code) {
        case CUSTOMER_CODE_REQUIRED -> result.add(CommercialOfferBlocker.CODE_REQUIRED);
        case CAMPAIGN_UNAVAILABLE -> result.add(CommercialOfferBlocker.CAMPAIGN_NOT_LIVE);
        case WINDOW_OUTSIDE_CAMPAIGN ->
            result.add(CommercialOfferBlocker.WINDOW_OUTSIDE_CAMPAIGN);
        case WINDOW_ENDED -> result.add(CommercialOfferBlocker.WINDOW_ENDED);
        default -> result.add(CommercialOfferBlocker.INVALID_SELECTION);
      }
    }
    return List.copyOf(new LinkedHashSet<>(result));
  }
}
