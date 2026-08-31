package com.hiveapp.platform.admin.service;

import com.hiveapp.identity.domain.constant.CredentialTokenPurpose;
import com.hiveapp.platform.admin.dto.PlatformCommunicationModels;
import com.hiveapp.shared.email.delivery.EmailDeliveryStatus;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PlatformCommunicationService {
    PlatformCommunicationModels.Summary summary();

    Page<PlatformCommunicationModels.Delivery> search(Query query, Pageable pageable);

    PlatformCommunicationModels.Delivery detail(UUID id);

    PlatformCommunicationModels.RecipientIdentity recipientIdentity(UUID id);

    PlatformCommunicationModels.FailureEvidence failureEvidence(UUID id);

    record Query(
            Instant from,
            Instant until,
            EmailDeliveryStatus status,
            CredentialTokenPurpose purpose,
            UUID accountId,
            UUID recipientUserId
    ) {}
}
