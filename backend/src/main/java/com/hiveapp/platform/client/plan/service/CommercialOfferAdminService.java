package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.account.dto.AccountDirectoryEntryDto;
import com.hiveapp.platform.client.plan.domain.constant.*;
import com.hiveapp.platform.client.plan.dto.*;
import java.util.*;
import org.springframework.data.domain.*;

public interface CommercialOfferAdminService {
  Page<CommercialOfferViews.Summary> list(
      String search, CommercialOfferStatus status, boolean includeArchived, Pageable pageable);

  CommercialOfferViews.Detail get(UUID id);

  CommercialOfferViews.OperationState operations(UUID id);

  CommercialOfferViews.EditableDefinition editableDefinition(UUID id);

  CommercialOfferViews.Mutation create(CommercialOfferRequests.Create r);

  CommercialOfferViews.Mutation update(UUID id, CommercialOfferRequests.Update r);

  CommercialOfferViews.Mutation duplicate(UUID id, CommercialOfferRequests.Duplicate r);

  CommercialOfferViews.Mutation revise(UUID id, CommercialOfferRequests.VersionReason r);

  Page<CommercialOfferViews.Revision> revisions(UUID id, Pageable p);

  CommercialOfferViews.Comparison compare(UUID a, UUID b);

  CommercialOfferViews.PublicationPreview previewPublication(UUID id);

  CommercialOfferViews.Mutation publish(UUID id, CommercialOfferRequests.Publish r);

  CommercialOfferViews.Mutation retire(UUID id, CommercialOfferRequests.VersionReason r);

  CommercialOfferViews.Mutation restore(UUID id, CommercialOfferRequests.Publish r);

  CommercialOfferViews.Mutation archive(UUID id, CommercialOfferRequests.VersionReason r);

  void deleteDraft(UUID id, CommercialOfferRequests.VersionReason r);

  CommercialOfferViews.Owner owner(UUID id);

  CommercialOfferViews.Mutation reassignOwner(UUID id, CommercialOfferRequests.ReassignOwner r);

  CommercialOfferViews.Stats stats(UUID id);

  Page<CommercialOfferViews.Redemption> redemptions(UUID id, Pageable p);

  Page<CommercialOfferViews.RedemptionIdentity> redemptionIdentities(UUID id, Pageable p);

  Page<CommercialOfferViews.History> history(UUID id, Pageable p);

  Page<AccountDirectoryEntryDto> chooseAccounts(String query, Boolean active, Pageable p);

  List<AccountDirectoryEntryDto> resolveAccountChoices(Collection<UUID> ids);

  Page<CommercialOfferViews.PricedChoice> chooseProducts(
      ProductPriceOwnerType type, String query, Pageable p);

  List<CommercialOfferViews.PricedChoice> resolveProductChoices(Collection<UUID> priceIds);

  List<CommercialOfferViews.QuotaResourceChoice> chooseQuotaResources(
      Collection<UUID> selectedPriceIds, String query);

  Page<CommercialOfferViews.OwnerChoice> chooseOwners(String query, Pageable p);

  List<CommercialOfferViews.OwnerChoice> resolveOwnerChoices(Collection<UUID> adminUserIds);

  Page<CommercialOfferViews.CampaignChoice> chooseCampaigns(String query, Pageable p);

  List<CommercialOfferViews.CampaignChoice> resolveCampaignChoices(Collection<UUID> campaignIds);

  CommercialOfferViews.EligibilityPreview previewForAccount(UUID offerId, UUID accountId);

  CommercialOfferViews.Acceptance applyForAccount(
      UUID offerId, UUID accountId, String idempotencyKey, CommercialOfferRequests.Accept request);
}
