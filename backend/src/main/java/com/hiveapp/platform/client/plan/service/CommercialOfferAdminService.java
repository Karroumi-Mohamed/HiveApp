package com.hiveapp.platform.client.plan.service;
import com.hiveapp.platform.client.plan.domain.constant.*;
import com.hiveapp.platform.client.plan.dto.*;
import org.springframework.data.domain.*;
import java.util.*;
import com.hiveapp.platform.client.account.dto.AccountDirectoryEntryDto;
public interface CommercialOfferAdminService{
 Page<CommercialOfferViews.Summary> list(String search,CommercialOfferStatus status,boolean includeArchived,Pageable pageable);
 CommercialOfferViews.Detail get(UUID id); CommercialOfferViews.Detail create(CommercialOfferRequests.Create r);
 CommercialOfferViews.Detail update(UUID id,CommercialOfferRequests.Update r); CommercialOfferViews.Detail duplicate(UUID id,CommercialOfferRequests.Duplicate r); CommercialOfferViews.Detail revise(UUID id,CommercialOfferRequests.VersionReason r);
 Page<CommercialOfferViews.Revision> revisions(UUID id,Pageable p); CommercialOfferViews.Comparison compare(UUID a,UUID b);
 CommercialOfferViews.PublicationPreview previewPublication(UUID id); CommercialOfferViews.Detail publish(UUID id,CommercialOfferRequests.Publish r);
 CommercialOfferViews.Detail retire(UUID id,CommercialOfferRequests.VersionReason r);CommercialOfferViews.Detail restore(UUID id,CommercialOfferRequests.VersionReason r);CommercialOfferViews.Detail archive(UUID id,CommercialOfferRequests.VersionReason r);void deleteDraft(UUID id,CommercialOfferRequests.VersionReason r);
 CommercialOfferViews.Owner owner(UUID id);CommercialOfferViews.Detail reassignOwner(UUID id,CommercialOfferRequests.ReassignOwner r);
 CommercialOfferViews.Stats stats(UUID id);Page<CommercialOfferViews.Redemption> redemptions(UUID id,Pageable p);Page<CommercialOfferViews.RedemptionIdentity> redemptionIdentities(UUID id,Pageable p);
 Page<CommercialOfferViews.History> history(UUID id,Pageable p);
 Page<AccountDirectoryEntryDto> chooseAccounts(String query,Boolean active,Pageable p);
 List<AccountDirectoryEntryDto> resolveAccountChoices(Collection<UUID> ids);
 Page<CommercialOfferViews.PricedChoice> chooseProducts(ProductPriceOwnerType type,String query,Pageable p);
 List<CommercialOfferViews.PricedChoice> resolveProductChoices(Collection<UUID> priceIds);
 List<CommercialOfferViews.QuotaResourceChoice> chooseQuotaResources(String query);
 Page<CommercialOfferViews.Owner> chooseOwners(String query,Pageable p);
 List<CommercialOfferViews.Owner> resolveOwnerChoices(Collection<UUID> adminUserIds);
 Page<CommercialOfferViews.Choice> chooseCampaigns(String query,Pageable p);
 List<CommercialOfferViews.Choice> resolveCampaignChoices(Collection<UUID> campaignIds);
 CommercialOfferViews.EligibilityPreview previewForAccount(UUID offerId,UUID accountId);
 CommercialOfferViews.Acceptance applyForAccount(UUID offerId,UUID accountId,String idempotencyKey,CommercialOfferRequests.Accept request);
}
