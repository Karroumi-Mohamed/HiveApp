package com.hiveapp.platform.communication;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class NotificationOfferStateTest {
  @Test
  void currentEligibilityIsRecheckedAndDeduplicatedWithinEachPage() {
    var offers = mock(NotificationOfferEligibility.class);
    @SuppressWarnings("unchecked")
    var provider = (org.springframework.beans.factory.ObjectProvider<NotificationOfferEligibility>) mock(org.springframework.beans.factory.ObjectProvider.class);
    when(provider.getObject()).thenReturn(offers);
    var sources = new CommunicationSources(mock(CommunicationEntryRepository.class),
        mock(com.hiveapp.platform.client.plan.domain.repository.PlanContentNoticeRepository.class),
        mock(com.hiveapp.platform.client.plan.domain.repository.SubscriptionRepricingItemRepository.class),
        mock(NotificationCatalog.class), provider);
    UUID account = UUID.randomUUID(), offer = UUID.randomUUID();
    var first = entry(account, offer);
    var second = entry(account, offer);
    assertThat(sources.states(List.of(first, second)).values()).containsOnly("PUBLISHED");
    verify(offers, times(1)).requireAvailable(account, offer);
    doThrow(new com.hiveapp.shared.exception.OfferNotAvailableException()).when(offers).requireAvailable(account, offer);
    assertThat(sources.states(List.of(first)).get(first.getId())).isEqualTo("UNAVAILABLE");
    assertThat(sources.inactive(first)).isTrue();
    assertThat(sources.states(List.of(entry(account, null))).values()).containsOnly("UNAVAILABLE");
  }

  private CommunicationEntry entry(UUID account, UUID offer) {
    var entry = new CommunicationEntry();
    org.springframework.test.util.ReflectionTestUtils.setField(entry, "id", UUID.randomUUID());
    entry.setSource("ADMIN");
    entry.setKind(CommunicationModels.Kind.OFFER);
    entry.setAccountId(account);
    entry.setResourceId(offer);
    return entry;
  }
}
