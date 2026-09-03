package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.SpecialAgreementStatus;
import com.hiveapp.platform.client.plan.dto.SpecialAgreementModels;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SpecialAgreementService {
    SpecialAgreementModels.Preview preview(
            UUID accountId, UUID actorUserId, SpecialAgreementModels.Definition definition);

    SpecialAgreementModels.Created confirm(
            UUID accountId, UUID actorUserId, SpecialAgreementModels.ConfirmRequest request);

    Page<SpecialAgreementModels.Summary> list(
            UUID accountId, SpecialAgreementStatus status, Pageable pageable);

    Page<SpecialAgreementModels.Summary> listAll(
            String search, SpecialAgreementStatus status, Pageable pageable);

    SpecialAgreementModels.Detail get(UUID accountId, UUID agreementId);

    SpecialAgreementModels.Detail cancel(
            UUID accountId, UUID agreementId, UUID actorUserId, String reason);

    SpecialAgreementModels.Detail retry(
            UUID accountId, UUID agreementId, UUID actorUserId, String reason);

    SpecialAgreementModels.Detail resolveManualReview(
            UUID accountId, UUID agreementId, UUID actorUserId, String reason);

    SpecialAgreementModels.Analytics analytics();
}
