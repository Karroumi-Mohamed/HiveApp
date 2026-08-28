package com.hiveapp.platform.client.plan.service;
import com.hiveapp.platform.client.plan.dto.*;
import org.springframework.data.domain.*;
import java.util.*;
public interface CommercialOfferService{
 Page<CommercialOfferViews.ClientOffer> catalogue(UUID accountId,Pageable p);
 CommercialOfferViews.ClientOffer detail(UUID accountId,UUID offerId);
 CommercialOfferViews.CodeResolution resolveCode(UUID accountId,UUID actor,CommercialOfferRequests.ResolveCode r);
 CommercialOfferViews.EligibilityPreview preview(UUID accountId,UUID actor,UUID offerId,String discoveryToken,boolean operator);
 CommercialOfferViews.Acceptance accept(UUID accountId,UUID actor,UUID offerId,String idempotencyKey,CommercialOfferRequests.Accept r,boolean operator);
 Page<CommercialOfferViews.ClientRedemption> history(UUID accountId,Pageable p);CommercialOfferViews.ClientRedemption redemption(UUID accountId,UUID id);
}
