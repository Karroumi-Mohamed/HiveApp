package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.CommercialCampaignStatus;
import com.hiveapp.platform.client.plan.domain.entity.CommercialCampaign;
import com.hiveapp.platform.client.plan.domain.repository.CommercialCampaignRepository;
import com.hiveapp.shared.audit.AuditTrail;
import com.hiveapp.shared.audit.domain.AuditActorSurface;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Isolated, lock-safe lifecycle transitions invoked without an authenticated administrator. */
@Service
@RequiredArgsConstructor
public class CommercialCampaignLifecycleTransitionService {

    private static final String RESOURCE = "COMMERCIAL_CAMPAIGN_ADMIN";
    private final CommercialCampaignRepository campaignRepository;
    private final CommercialCatalogVersionService catalogVersionService;
    private final AuditTrail auditTrail;

    @Transactional
    public boolean processDueStart(UUID campaignId, Instant now) {
        UUID catalogRevisionId = catalogVersionService.lockForMutation().getId();
        CommercialCampaign campaign = campaignRepository.findByIdForUpdate(campaignId).orElse(null);
        if (campaign == null || campaign.getStatus() != CommercialCampaignStatus.SCHEDULED
                || campaign.getStartsAt().isAfter(now)) return false;
        CommercialCampaignStatus before = campaign.getStatus();
        String action;
        if (!campaign.getEndsAt().isAfter(now)) {
            campaign.end(now);
            action = "platform.campaigns.process_due_end";
        } else {
            campaign.start(now);
            action = "platform.campaigns.process_due_start";
        }
        campaignRepository.saveAndFlush(campaign);
        recordMutation(action, campaign, before, now);
        catalogVersionService.bump(catalogRevisionId);
        return true;
    }

    @Transactional
    public boolean processDueEnd(UUID campaignId, Instant now) {
        UUID catalogRevisionId = catalogVersionService.lockForMutation().getId();
        CommercialCampaign campaign = campaignRepository.findByIdForUpdate(campaignId).orElse(null);
        if (campaign == null || !SetHolder.ENDABLE.contains(campaign.getStatus())
                || campaign.getEndsAt().isAfter(now)) return false;
        CommercialCampaignStatus before = campaign.getStatus();
        campaign.end(now);
        campaignRepository.saveAndFlush(campaign);
        recordMutation("platform.campaigns.process_due_end", campaign, before, now);
        catalogVersionService.bump(catalogRevisionId);
        return true;
    }

    private void recordMutation(String action, CommercialCampaign campaign,
                                CommercialCampaignStatus before, Instant processedAt) {
        auditTrail.recordSuccess(action, RESOURCE, campaign.getId(), AuditActorSurface.SYSTEM,
                null, null,
                Map.of("status", before.name(), "processedAt", processedAt),
                Map.of("status", campaign.getStatus().name(), "processedAt", processedAt));
    }

    private static final class SetHolder {
        private static final java.util.Set<CommercialCampaignStatus> ENDABLE = java.util.Set.of(
                CommercialCampaignStatus.SCHEDULED,
                CommercialCampaignStatus.ACTIVE,
                CommercialCampaignStatus.PAUSED);
    }
}
