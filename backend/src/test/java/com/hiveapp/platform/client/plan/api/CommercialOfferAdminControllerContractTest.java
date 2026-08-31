package com.hiveapp.platform.client.plan.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hiveapp.platform.client.plan.domain.constant.CommercialOfferPublicationMode;
import com.hiveapp.platform.client.plan.dto.CommercialOfferRequests;
import com.hiveapp.platform.client.plan.dto.CommercialOfferViews;
import com.hiveapp.platform.client.plan.service.CommercialOfferAdminService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

class CommercialOfferAdminControllerContractTest {
  @Test
  void allReviewResponsesDisableStorageIncludingPublicationGetEvidence() {
    CommercialOfferAdminService service = mock(CommercialOfferAdminService.class);
    CommercialOfferAdminController controller = new CommercialOfferAdminController(service);
    UUID offerId = UUID.randomUUID();
    UUID accountId = UUID.randomUUID();
    Instant now = Instant.parse("2026-08-28T12:00:00Z");
    var definition =
        new CommercialOfferViews.DefinitionPreview(
            null, null, false, true, null, null, List.of());
    var publication =
        new CommercialOfferViews.PublicationPreview(
            offerId,
            3,
            CommercialOfferPublicationMode.PUBLISH,
            true,
            List.of(),
            List.of(),
            now,
            now.plusSeconds(300),
            "signed-review");
    var assessment =
        new CommercialOfferViews.AccountEligibilityAssessment(
            offerId, accountId, false, List.of(), now, null);
    when(service.previewCreateDefinition((CommercialOfferRequests.Create) null))
        .thenReturn(definition);
    when(service.previewPublication(offerId)).thenReturn(publication);
    when(service.previewForAccount(offerId, accountId)).thenReturn(assessment);

    assertNoStore(controller.previewCreateDefinition(null));
    assertNoStore(controller.preview(offerId));
    assertNoStore(controller.previewAccount(offerId, accountId));
  }

  private void assertNoStore(ResponseEntity<?> response) {
    assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
  }
}
