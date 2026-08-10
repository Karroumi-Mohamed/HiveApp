package com.hiveapp.platform.client.collaboration.service;

import com.hiveapp.platform.client.collaboration.domain.constant.CollaborationStatus;
import com.hiveapp.platform.client.collaboration.domain.entity.Collaboration;
import com.hiveapp.platform.client.collaboration.domain.repository.CollaborationRepository;
import com.hiveapp.platform.client.plan.service.PlanEntitlementService;
import com.hiveapp.platform.registry.definition.B2bFeature;
import com.hiveapp.shared.audit.AuditedMutation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CollaborationAutomaticResumeService {

    private final CollaborationRepository collaborationRepository;
    private final PlanEntitlementService planEntitlementService;
    private final Clock clock;

    @Transactional
    @AuditedMutation(
            action = "platform.client.b2b.automatic_resume",
            resourceType = "COLLABORATION_BATCH")
    public int resumeDueCollaborations() {
        Instant now = clock.instant();
        int resumed = 0;
        Map<UUID, Boolean> providerEntitlement = new HashMap<>();
        for (Collaboration collaboration : collaborationRepository
                .findDueAutomaticResumesForUpdate(CollaborationStatus.SUSPENDED, now)) {
            UUID providerAccountId = collaboration.getProviderAccount().getId();
            boolean providerCanResume = providerEntitlement.computeIfAbsent(
                    providerAccountId,
                    id -> planEntitlementService.isPermissionEntitled(
                            id, B2bFeature.CODE + ".resume"));
            if (!activeScope(collaboration) || !providerCanResume) {
                continue;
            }
            collaboration.setStatus(CollaborationStatus.ACTIVE);
            collaboration.setResumedAt(now);
            collaboration.setResumedByUserId(null);
            collaboration.setSuspensionReviewAt(null);
            collaboration.setAutomaticResumeAt(null);
            collaboration.setLifecycleReason("Automatically resumed at the provider-configured time");
            resumed++;
        }
        return resumed;
    }

    private boolean activeScope(Collaboration collaboration) {
        return collaboration.getClientAccount().isActive()
                && collaboration.getProviderAccount().isActive()
                && collaboration.getCompany().isActive();
    }
}
