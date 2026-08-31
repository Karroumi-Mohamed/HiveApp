package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.CommercialCampaignStatus;
import com.hiveapp.platform.client.plan.domain.repository.CommercialCampaignRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Fetches bounded work; each Campaign transition commits independently. */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommercialCampaignLifecycleProcessor {

    static final int BATCH_SIZE = 100;
    private static final Set<CommercialCampaignStatus> ENDABLE = Set.of(
            CommercialCampaignStatus.SCHEDULED,
            CommercialCampaignStatus.ACTIVE,
            CommercialCampaignStatus.PAUSED);

    private final CommercialCampaignRepository campaignRepository;
    private final CommercialCampaignLifecycleTransitionService transitions;

    public void processDue(Instant now) {
        for (UUID id : campaignRepository.findDueStarts(CommercialCampaignStatus.SCHEDULED,
                now, PageRequest.of(0, BATCH_SIZE))) {
            safely(id, () -> transitions.processDueStart(id, now));
        }
        for (UUID id : campaignRepository.findDueEnds(ENDABLE, now,
                PageRequest.of(0, BATCH_SIZE))) {
            safely(id, () -> transitions.processDueEnd(id, now));
        }
    }

    private void safely(UUID campaignId, Runnable operation) {
        try {
            operation.run();
        } catch (RuntimeException failure) {
            log.error("Campaign lifecycle processing failed for {}", campaignId, failure);
        }
    }
}
