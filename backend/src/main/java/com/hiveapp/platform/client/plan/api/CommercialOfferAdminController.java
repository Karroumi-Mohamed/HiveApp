package com.hiveapp.platform.client.plan.api;

import com.hiveapp.platform.client.account.dto.AccountDirectoryEntryDto;
import com.hiveapp.platform.client.plan.domain.constant.CommercialOfferAcceptance;
import com.hiveapp.platform.client.plan.domain.constant.CommercialOfferDiscovery;
import com.hiveapp.platform.client.plan.domain.constant.CommercialOfferRedemptionStatus;
import com.hiveapp.platform.client.plan.domain.constant.CommercialOfferStatus;
import com.hiveapp.platform.client.plan.domain.constant.CommercialOfferSurface;
import com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType;
import com.hiveapp.platform.client.plan.dto.*;
import com.hiveapp.platform.client.plan.service.CommercialOfferAdminService;
import com.hiveapp.shared.api.PageResponse;
import jakarta.validation.Valid;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/offers")
@RequiredArgsConstructor
public class CommercialOfferAdminController {
  private static final Map<String, String> SORTS =
      Map.of(
          "createdAt",
          "createdAt",
          "updatedAt",
          "updatedAt",
          "code",
          "businessCode",
          "name",
          "name",
          "status",
          "status",
          "startsAt",
          "startsAt",
          "endsAt",
          "endsAt",
          "revisionNumber",
          "revisionNumber");
  private static final Map<String, String> REDEMPTION_SORTS =
      Map.of(
          "reservedAt", "reservedAt",
          "appliedAt", "appliedAt",
          "releasedAt", "releasedAt",
          "status", "status",
          "surface", "surface");
  private final CommercialOfferAdminService service;

  @GetMapping
  public PageResponse<CommercialOfferViews.Summary> list(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) CommercialOfferStatus status,
      @RequestParam(required = false) UUID campaignId,
      @RequestParam(required = false) CommercialOfferDiscovery discovery,
      @RequestParam(required = false) CommercialOfferAcceptance acceptance,
      @RequestParam(defaultValue = "false") boolean includeArchived,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String sort,
      @RequestParam(required = false) String direction) {
    return PageResponse.from(
        service.list(
            search,
            status,
            campaignId,
            discovery,
            acceptance,
            includeArchived,
            CommercialProductPageRequest.of(
                page, size, sort, direction, SORTS, "createdAt", Sort.Direction.DESC)));
  }

  @GetMapping("/{id}")
  public CommercialOfferViews.Detail get(@PathVariable UUID id) {
    return service.get(id);
  }

  @GetMapping("/{id}/operations")
  public CommercialOfferViews.OperationState operations(@PathVariable UUID id) {
    return service.operations(id);
  }

  @GetMapping("/{id}/editable-definition")
  public CommercialOfferViews.EditableDefinition editableDefinition(@PathVariable UUID id) {
    return service.editableDefinition(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public CommercialOfferViews.Mutation create(
      @Valid @RequestBody CommercialOfferRequests.Create r) {
    return service.create(r);
  }

  @PostMapping("/definition-preview")
  public ResponseEntity<CommercialOfferViews.DefinitionPreview> previewCreateDefinition(
      @Valid @RequestBody CommercialOfferRequests.Create request) {
    return noStore(service.previewCreateDefinition(request));
  }

  @PutMapping("/{id}")
  public CommercialOfferViews.Mutation update(
      @PathVariable UUID id, @Valid @RequestBody CommercialOfferRequests.Update r) {
    return service.update(id, r);
  }

  @PostMapping("/{id}/definition-preview")
  public ResponseEntity<CommercialOfferViews.DefinitionPreview> previewUpdateDefinition(
      @PathVariable UUID id, @Valid @RequestBody CommercialOfferRequests.Update request) {
    return noStore(service.previewUpdateDefinition(id, request));
  }

  @PostMapping("/{id}/duplicate")
  @ResponseStatus(HttpStatus.CREATED)
  public CommercialOfferViews.Mutation duplicate(
      @PathVariable UUID id, @Valid @RequestBody CommercialOfferRequests.Duplicate r) {
    return service.duplicate(id, r);
  }

  @PostMapping("/{id}/revisions")
  @ResponseStatus(HttpStatus.CREATED)
  public CommercialOfferViews.Mutation revise(
      @PathVariable UUID id, @Valid @RequestBody CommercialOfferRequests.VersionReason r) {
    return service.revise(id, r);
  }

  @GetMapping("/{id}/revisions")
  public PageResponse<CommercialOfferViews.Revision> revisions(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return PageResponse.from(service.revisions(id, page(page, size)));
  }

  @GetMapping("/{id}/compare/{other}")
  public CommercialOfferViews.Comparison compare(@PathVariable UUID id, @PathVariable UUID other) {
    return service.compare(id, other);
  }

  @GetMapping("/{id}/publication-preview")
  public ResponseEntity<CommercialOfferViews.PublicationPreview> preview(@PathVariable UUID id) {
    return noStore(service.previewPublication(id));
  }

  @PostMapping("/{id}/publish")
  public CommercialOfferViews.Mutation publish(
      @PathVariable UUID id, @Valid @RequestBody CommercialOfferRequests.Publish r) {
    return service.publish(id, r);
  }

  @PostMapping("/{id}/retire")
  public CommercialOfferViews.Mutation retire(
      @PathVariable UUID id, @Valid @RequestBody CommercialOfferRequests.VersionReason r) {
    return service.retire(id, r);
  }

  @PostMapping("/{id}/restore")
  public CommercialOfferViews.Mutation restore(
      @PathVariable UUID id, @Valid @RequestBody CommercialOfferRequests.Publish r) {
    return service.restore(id, r);
  }

  @PostMapping("/{id}/archive")
  public CommercialOfferViews.Mutation archive(
      @PathVariable UUID id, @Valid @RequestBody CommercialOfferRequests.VersionReason r) {
    return service.archive(id, r);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @PathVariable UUID id, @Valid @RequestBody CommercialOfferRequests.VersionReason r) {
    service.deleteDraft(id, r);
  }

  @GetMapping("/{id}/owner")
  public CommercialOfferViews.Owner owner(@PathVariable UUID id) {
    return service.owner(id);
  }

  @PutMapping("/{id}/owner")
  public CommercialOfferViews.Mutation owner(
      @PathVariable UUID id, @Valid @RequestBody CommercialOfferRequests.ReassignOwner r) {
    return service.reassignOwner(id, r);
  }

  @GetMapping("/{id}/stats")
  public CommercialOfferViews.Stats stats(@PathVariable UUID id) {
    return service.stats(id);
  }

  @GetMapping("/{id}/history")
  public PageResponse<CommercialOfferViews.History> history(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return PageResponse.from(service.history(id, page(page, size)));
  }

  @GetMapping("/{id}/redemptions")
  public PageResponse<CommercialOfferViews.Redemption> redemptions(
      @PathVariable UUID id,
      @RequestParam(required = false) CommercialOfferRedemptionStatus status,
      @RequestParam(required = false) CommercialOfferSurface surface,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String sort,
      @RequestParam(required = false) String direction) {
    return PageResponse.from(
        service.redemptions(
            id, status, surface, redemptionPage(page, size, sort, direction)));
  }

  @GetMapping("/{id}/redemptions/{redemptionId}")
  public CommercialOfferViews.Redemption redemption(
      @PathVariable UUID id, @PathVariable UUID redemptionId) {
    return service.redemption(id, redemptionId);
  }

  @PostMapping("/{id}/redemption-identity-resolution")
  public List<CommercialOfferViews.RedemptionIdentity> identities(
      @PathVariable UUID id,
      @Valid @RequestBody CommercialOfferRequests.RedemptionIdentityResolution request) {
    return service.resolveRedemptionIdentities(id, request.redemptionIds());
  }

  @GetMapping("/account-choices")
  public PageResponse<AccountDirectoryEntryDto> chooseAccounts(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) Boolean active,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return PageResponse.from(service.chooseAccounts(search, active, page(page, size)));
  }

  @PostMapping("/account-choice-resolution")
  public List<AccountDirectoryEntryDto> resolveAccounts(@RequestBody List<UUID> ids) {
    return service.resolveAccountChoices(ids);
  }

  @GetMapping("/product-price-choices")
  public PageResponse<CommercialOfferViews.PricedChoice> chooseProducts(
      @RequestParam ProductPriceOwnerType ownerType,
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return PageResponse.from(service.chooseProducts(ownerType, search, page(page, size)));
  }

  @PostMapping("/product-price-choice-resolution")
  public List<CommercialOfferViews.PricedChoice> resolveProducts(@RequestBody List<UUID> priceIds) {
    return service.resolveProductChoices(priceIds);
  }

  @GetMapping("/quota-resource-choices")
  public List<CommercialOfferViews.QuotaResourceChoice> quotaResources(
      @RequestParam List<UUID> selectedPriceIds, @RequestParam(required = false) String search) {
    return service.chooseQuotaResources(selectedPriceIds, search);
  }

  @GetMapping("/owner-choices")
  public PageResponse<CommercialOfferViews.OwnerChoice> owners(
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return PageResponse.from(service.chooseOwners(search, page(page, size)));
  }

  @PostMapping("/owner-choice-resolution")
  public List<CommercialOfferViews.OwnerChoice> resolveOwners(@RequestBody List<UUID> ids) {
    return service.resolveOwnerChoices(ids);
  }

  @GetMapping("/campaign-choices")
  public PageResponse<CommercialOfferViews.CampaignChoice> campaigns(
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return PageResponse.from(service.chooseCampaigns(search, page(page, size)));
  }

  @PostMapping("/campaign-choice-resolution")
  public List<CommercialOfferViews.CampaignChoice> resolveCampaigns(@RequestBody List<UUID> ids) {
    return service.resolveCampaignChoices(ids);
  }

  @PostMapping("/{id}/accounts/{accountId}/preview")
  public ResponseEntity<CommercialOfferViews.AccountEligibilityAssessment> previewAccount(
      @PathVariable UUID id, @PathVariable UUID accountId) {
    return noStore(service.previewForAccount(id, accountId));
  }

  @PostMapping("/{id}/accounts/{accountId}/apply")
  public ResponseEntity<CommercialOfferViews.AdminAcceptance> applyAccount(
      @PathVariable UUID id,
      @PathVariable UUID accountId,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody CommercialOfferRequests.OperatorAccept request) {
    var result = service.applyForAccount(id, accountId, key, request);
    return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
        .body(result);
  }

  private org.springframework.data.domain.Pageable page(int p, int s) {
    return CommercialProductPageRequest.of(
        p, s, null, null, Map.of("createdAt", "createdAt"), "createdAt", Sort.Direction.DESC);
  }

  private org.springframework.data.domain.Pageable redemptionPage(
      int page, int size, String sort, String direction) {
    return CommercialProductPageRequest.of(
        page,
        size,
        sort,
        direction,
        REDEMPTION_SORTS,
        "reservedAt",
        Sort.Direction.DESC);
  }

  private <T> ResponseEntity<T> noStore(T body) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
  }
}
