package com.hiveapp.platform.client.plan.api;

import com.hiveapp.platform.client.plan.dto.*;
import com.hiveapp.platform.client.plan.service.SubscriptionService;
import com.hiveapp.shared.api.PageResponse;
import com.hiveapp.shared.security.context.HiveAppContextHolder;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/subscriptions/offers")
@RequiredArgsConstructor
public class CommercialOfferController {
  private static final Map<String, String> OFFER_SORTS =
      Map.of(
          "createdAt", "createdAt",
          "name", "name",
          "startsAt", "startsAt",
          "endsAt", "endsAt");
  private static final Map<String, String> REDEMPTION_SORTS =
      Map.of(
          "reservedAt", "reservedAt",
          "appliedAt", "appliedAt",
          "status", "status");

  private final SubscriptionService service;

  @GetMapping
  public PageResponse<CommercialOfferViews.ClientOffer> catalogue(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String sort,
      @RequestParam(required = false) String direction) {
    var c = HiveAppContextHolder.getContext();
    return PageResponse.from(
        service.offerCatalogue(c.currentAccountId(), offerPage(page, size, sort, direction)));
  }

  @GetMapping("/{id}")
  public CommercialOfferViews.ClientOffer detail(@PathVariable UUID id) {
    var c = HiveAppContextHolder.getContext();
    return service.offerDetail(c.currentAccountId(), id);
  }

  @PostMapping("/code-resolution")
  public CommercialOfferViews.CodeResolution resolve(
      @Valid @RequestBody CommercialOfferRequests.ResolveCode r) {
    var c = HiveAppContextHolder.getContext();
    return service.resolveOfferCode(c.currentAccountId(), c.actorUserId(), r);
  }

  @PostMapping("/{id}/preview")
  public CommercialOfferViews.EligibilityPreview preview(
      @PathVariable UUID id, @Valid @RequestBody CommercialOfferRequests.Preview r) {
    var c = HiveAppContextHolder.getContext();
    return service.previewOffer(c.currentAccountId(), c.actorUserId(), id, r);
  }

  @PostMapping("/{id}/accept")
  public ResponseEntity<CommercialOfferViews.Acceptance> accept(
      @PathVariable UUID id,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody CommercialOfferRequests.Accept r) {
    var c = HiveAppContextHolder.getContext();
    var result = service.acceptOffer(c.currentAccountId(), c.actorUserId(), id, key, r);
    return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
        .body(result);
  }

  @GetMapping("/redemptions")
  public PageResponse<CommercialOfferViews.ClientRedemption> history(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String sort,
      @RequestParam(required = false) String direction) {
    var c = HiveAppContextHolder.getContext();
    return PageResponse.from(
        service.offerHistory(c.currentAccountId(), redemptionPage(page, size, sort, direction)));
  }

  @GetMapping("/redemptions/{id}")
  public CommercialOfferViews.ClientRedemption redemption(@PathVariable UUID id) {
    var c = HiveAppContextHolder.getContext();
    return service.offerRedemption(c.currentAccountId(), id);
  }

  private org.springframework.data.domain.Pageable offerPage(
      int page, int size, String sort, String direction) {
    return CommercialProductPageRequest.of(
        page, size, sort, direction, OFFER_SORTS, "createdAt", Sort.Direction.DESC);
  }

  private org.springframework.data.domain.Pageable redemptionPage(
      int page, int size, String sort, String direction) {
    return CommercialProductPageRequest.of(
        page, size, sort, direction, REDEMPTION_SORTS, "reservedAt", Sort.Direction.DESC);
  }
}
