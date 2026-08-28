package com.hiveapp.platform.client.plan.api;
import com.hiveapp.platform.client.plan.domain.constant.CommercialOfferStatus;
import com.hiveapp.platform.client.plan.domain.constant.ProductPriceOwnerType;
import com.hiveapp.platform.client.plan.dto.*;
import com.hiveapp.platform.client.plan.service.CommercialOfferAdminService;
import com.hiveapp.shared.api.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import com.hiveapp.platform.client.account.dto.AccountDirectoryEntryDto;

@RestController @RequestMapping("/api/admin/offers") @RequiredArgsConstructor
public class CommercialOfferAdminController{
 private static final Map<String,String>SORTS=Map.of("createdAt","createdAt","updatedAt","updatedAt","code","businessCode","name","name","status","status","startsAt","startsAt","endsAt","endsAt","revisionNumber","revisionNumber");
 private final CommercialOfferAdminService service;
 @GetMapping public PageResponse<CommercialOfferViews.Summary> list(@RequestParam(required=false)String search,@RequestParam(required=false)CommercialOfferStatus status,@RequestParam(defaultValue="false")boolean includeArchived,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,@RequestParam(required=false)String sort,@RequestParam(required=false)String direction){return PageResponse.from(service.list(search,status,includeArchived,CommercialProductPageRequest.of(page,size,sort,direction,SORTS,"createdAt",Sort.Direction.DESC)));}
 @GetMapping("/{id}")public CommercialOfferViews.Detail get(@PathVariable UUID id){return service.get(id);}
 @PostMapping @ResponseStatus(HttpStatus.CREATED)public CommercialOfferViews.Detail create(@Valid@RequestBody CommercialOfferRequests.Create r){return service.create(r);}
 @PutMapping("/{id}")public CommercialOfferViews.Detail update(@PathVariable UUID id,@Valid@RequestBody CommercialOfferRequests.Update r){return service.update(id,r);}
 @PostMapping("/{id}/duplicate")@ResponseStatus(HttpStatus.CREATED)public CommercialOfferViews.Detail duplicate(@PathVariable UUID id,@Valid@RequestBody CommercialOfferRequests.Duplicate r){return service.duplicate(id,r);}
 @PostMapping("/{id}/revisions")@ResponseStatus(HttpStatus.CREATED)public CommercialOfferViews.Detail revise(@PathVariable UUID id,@Valid@RequestBody CommercialOfferRequests.VersionReason r){return service.revise(id,r);}
 @GetMapping("/{id}/revisions")public PageResponse<CommercialOfferViews.Revision> revisions(@PathVariable UUID id,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return PageResponse.from(service.revisions(id,page(page,size)));}
 @GetMapping("/{id}/compare/{other}")public CommercialOfferViews.Comparison compare(@PathVariable UUID id,@PathVariable UUID other){return service.compare(id,other);}
 @GetMapping("/{id}/publication-preview")public CommercialOfferViews.PublicationPreview preview(@PathVariable UUID id){return service.previewPublication(id);}
 @PostMapping("/{id}/publish")public CommercialOfferViews.Detail publish(@PathVariable UUID id,@Valid@RequestBody CommercialOfferRequests.Publish r){return service.publish(id,r);}
 @PostMapping("/{id}/retire")public CommercialOfferViews.Detail retire(@PathVariable UUID id,@Valid@RequestBody CommercialOfferRequests.VersionReason r){return service.retire(id,r);}
 @PostMapping("/{id}/restore")public CommercialOfferViews.Detail restore(@PathVariable UUID id,@Valid@RequestBody CommercialOfferRequests.VersionReason r){return service.restore(id,r);}
 @PostMapping("/{id}/archive")public CommercialOfferViews.Detail archive(@PathVariable UUID id,@Valid@RequestBody CommercialOfferRequests.VersionReason r){return service.archive(id,r);}
 @DeleteMapping("/{id}")@ResponseStatus(HttpStatus.NO_CONTENT)public void delete(@PathVariable UUID id,@Valid@RequestBody CommercialOfferRequests.VersionReason r){service.deleteDraft(id,r);}
 @GetMapping("/{id}/owner")public CommercialOfferViews.Owner owner(@PathVariable UUID id){return service.owner(id);}
 @PutMapping("/{id}/owner")public CommercialOfferViews.Detail owner(@PathVariable UUID id,@Valid@RequestBody CommercialOfferRequests.ReassignOwner r){return service.reassignOwner(id,r);}
 @GetMapping("/{id}/stats")public CommercialOfferViews.Stats stats(@PathVariable UUID id){return service.stats(id);}
 @GetMapping("/{id}/history")public PageResponse<CommercialOfferViews.History> history(@PathVariable UUID id,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return PageResponse.from(service.history(id,page(page,size)));}
 @GetMapping("/{id}/redemptions")public PageResponse<CommercialOfferViews.Redemption> redemptions(@PathVariable UUID id,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return PageResponse.from(service.redemptions(id,page(page,size)));}
 @GetMapping("/{id}/redemption-identities")public PageResponse<CommercialOfferViews.RedemptionIdentity> identities(@PathVariable UUID id,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return PageResponse.from(service.redemptionIdentities(id,page(page,size)));}
 @GetMapping("/account-choices")public PageResponse<AccountDirectoryEntryDto> chooseAccounts(@RequestParam(required=false)String search,@RequestParam(required=false)Boolean active,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return PageResponse.from(service.chooseAccounts(search,active,page(page,size)));}
 @PostMapping("/account-choice-resolution")public List<AccountDirectoryEntryDto> resolveAccounts(@RequestBody List<UUID> ids){return service.resolveAccountChoices(ids);}
 @GetMapping("/product-price-choices")public PageResponse<CommercialOfferViews.PricedChoice> chooseProducts(@RequestParam ProductPriceOwnerType ownerType,@RequestParam(required=false)String search,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return PageResponse.from(service.chooseProducts(ownerType,search,page(page,size)));}
 @PostMapping("/product-price-choice-resolution")public List<CommercialOfferViews.PricedChoice> resolveProducts(@RequestBody List<UUID> priceIds){return service.resolveProductChoices(priceIds);}
 @GetMapping("/quota-resource-choices")public List<CommercialOfferViews.QuotaResourceChoice> quotaResources(@RequestParam(required=false)String search){return service.chooseQuotaResources(search);}
 @GetMapping("/owner-choices")public PageResponse<CommercialOfferViews.Owner> owners(@RequestParam(required=false)String search,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return PageResponse.from(service.chooseOwners(search,page(page,size)));}
 @PostMapping("/owner-choice-resolution")public List<CommercialOfferViews.Owner> resolveOwners(@RequestBody List<UUID> ids){return service.resolveOwnerChoices(ids);}
 @GetMapping("/campaign-choices")public PageResponse<CommercialOfferViews.Choice> campaigns(@RequestParam(required=false)String search,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return PageResponse.from(service.chooseCampaigns(search,page(page,size)));}
 @PostMapping("/campaign-choice-resolution")public List<CommercialOfferViews.Choice> resolveCampaigns(@RequestBody List<UUID> ids){return service.resolveCampaignChoices(ids);}
 @PostMapping("/{id}/accounts/{accountId}/preview")public CommercialOfferViews.EligibilityPreview previewAccount(@PathVariable UUID id,@PathVariable UUID accountId){return service.previewForAccount(id,accountId);}
 @PostMapping("/{id}/accounts/{accountId}/apply")public ResponseEntity<CommercialOfferViews.Acceptance> applyAccount(@PathVariable UUID id,@PathVariable UUID accountId,@RequestHeader("Idempotency-Key")String key,@Valid@RequestBody CommercialOfferRequests.Accept request){var result=service.applyForAccount(id,accountId,key,request);return ResponseEntity.status(result.replayed()?HttpStatus.OK:HttpStatus.CREATED).body(result);}
 private org.springframework.data.domain.Pageable page(int p,int s){return CommercialProductPageRequest.of(p,s,null,null,Map.of(),"createdAt",Sort.Direction.DESC);}
}
