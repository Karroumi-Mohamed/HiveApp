package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.account.dto.AccountDirectoryEntryDto;
import com.hiveapp.platform.client.plan.domain.constant.CommercialSegmentKind;
import com.hiveapp.platform.client.plan.domain.constant.CommercialSegmentSource;
import com.hiveapp.platform.client.plan.domain.constant.CommercialSegmentStatus;
import com.hiveapp.platform.client.plan.dto.CommercialSegmentRequests;
import com.hiveapp.platform.client.plan.dto.CommercialSegmentViews;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CommercialSegmentAdminService {
    Page<CommercialSegmentViews.Summary> list(
            String search,
            CommercialSegmentStatus status,
            CommercialSegmentKind kind,
            CommercialSegmentSource source,
            boolean includeArchived,
            Pageable pageable);

    CommercialSegmentViews.Detail get(UUID segmentId);
    CommercialSegmentViews.Detail create(CommercialSegmentRequests.Create request);
    CommercialSegmentViews.Detail update(UUID segmentId, CommercialSegmentRequests.Update request);
    CommercialSegmentViews.Detail duplicate(UUID segmentId, CommercialSegmentRequests.Duplicate request);
    CommercialSegmentViews.Detail revise(UUID segmentId, CommercialSegmentRequests.VersionReason request);
    CommercialSegmentViews.Comparison compare(UUID segmentId, UUID comparedSegmentId);
    Page<CommercialSegmentViews.Revision> revisions(UUID segmentId, Pageable pageable);
    Page<CommercialSegmentViews.History> history(UUID segmentId, Pageable pageable);
    CommercialSegmentViews.Count count(UUID segmentId);
    CommercialSegmentViews.Preview preview(UUID segmentId);
    CommercialSegmentViews.IdentitySample previewIdentities(UUID segmentId);
    CommercialSegmentViews.Detail activate(UUID segmentId, CommercialSegmentRequests.Activation request);
    CommercialSegmentViews.Detail archive(UUID segmentId, CommercialSegmentRequests.VersionReason request);
    void deleteDraft(UUID segmentId, CommercialSegmentRequests.VersionReason request);
    Page<CommercialSegmentViews.Activation> activations(UUID segmentId, Pageable pageable);
    CommercialSegmentViews.ActivationAudience activationAudience(
            UUID segmentId, UUID activationId, Pageable pageable);
    CommercialSegmentViews.ActivationIdentityAudience activationIdentities(
            UUID segmentId, UUID activationId, Pageable pageable);
    CommercialSegmentViews.Owner owner(UUID segmentId);
    CommercialSegmentViews.Detail reassignOwner(
            UUID segmentId, CommercialSegmentRequests.ReassignOwner request);
    Page<AccountDirectoryEntryDto> chooseAccounts(String query, Boolean active, Pageable pageable);
    List<AccountDirectoryEntryDto> resolveAccountChoices(Collection<UUID> ids);
}
