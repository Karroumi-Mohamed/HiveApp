package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.CommercialCampaignStatus;
import com.hiveapp.platform.client.plan.domain.repository.CommercialCampaignRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommercialCampaignLifecycleProcessorTest {

    private static final Instant NOW = Instant.parse("2026-08-28T10:00:00Z");

    @Mock private CommercialCampaignRepository campaignRepository;
    @Mock private CommercialCampaignLifecycleTransitionService transitions;

    @InjectMocks private CommercialCampaignLifecycleProcessor processor;

    @Test
    void oneBrokenCampaignDoesNotBlockLaterStartsOrEndsInTheBoundedBatch() {
        UUID brokenStart = UUID.randomUUID();
        UUID healthyStart = UUID.randomUUID();
        UUID brokenEnd = UUID.randomUUID();
        UUID healthyEnd = UUID.randomUUID();
        when(campaignRepository.findDueStarts(
                eq(CommercialCampaignStatus.SCHEDULED), eq(NOW),
                argThat(page -> page.getPageNumber() == 0
                        && page.getPageSize() == CommercialCampaignLifecycleProcessor.BATCH_SIZE)))
                .thenReturn(List.of(brokenStart, healthyStart));
        when(campaignRepository.findDueEnds(
                eq(Set.of(CommercialCampaignStatus.SCHEDULED,
                        CommercialCampaignStatus.ACTIVE,
                        CommercialCampaignStatus.PAUSED)), eq(NOW),
                argThat(page -> page.getPageNumber() == 0
                        && page.getPageSize() == CommercialCampaignLifecycleProcessor.BATCH_SIZE)))
                .thenReturn(List.of(brokenEnd, healthyEnd));
        when(transitions.processDueStart(brokenStart, NOW))
                .thenThrow(new IllegalStateException("broken start"));
        when(transitions.processDueStart(healthyStart, NOW)).thenReturn(true);
        when(transitions.processDueEnd(brokenEnd, NOW))
                .thenThrow(new IllegalStateException("broken end"));
        when(transitions.processDueEnd(healthyEnd, NOW)).thenReturn(true);

        processor.processDue(NOW);

        verify(transitions).processDueStart(brokenStart, NOW);
        verify(transitions).processDueStart(healthyStart, NOW);
        verify(transitions).processDueEnd(brokenEnd, NOW);
        verify(transitions).processDueEnd(healthyEnd, NOW);
    }
}
