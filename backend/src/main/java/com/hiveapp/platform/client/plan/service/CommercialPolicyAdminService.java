package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicySource;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyStatus;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyTargetKind;
import com.hiveapp.platform.client.plan.dto.CommercialPolicyRequests;
import com.hiveapp.platform.client.plan.dto.CommercialPolicyViews;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.hiveapp.platform.client.account.dto.AccountDirectoryEntryDto;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CommercialPolicyAdminService {
    Page<CommercialPolicyViews.Summary> list(
            String search,
            CommercialPolicyStatus status,
            CommercialPolicyTargetKind targetKind,
            CommercialPolicySource source,
            Instant effectiveAt,
            boolean includeArchived,
            Pageable pageable);

    CommercialPolicyViews.Detail get(UUID policyId);
    CommercialPolicyViews.Detail create(CommercialPolicyRequests.Create request);
    CommercialPolicyViews.Detail update(UUID policyId, CommercialPolicyRequests.Update request);
    CommercialPolicyViews.Detail duplicate(UUID policyId, CommercialPolicyRequests.Duplicate request);
    CommercialPolicyViews.Detail revise(UUID policyId, CommercialPolicyRequests.VersionReason request);
    CommercialPolicyViews.Comparison compare(UUID policyId, UUID comparedPolicyId);
    Page<CommercialPolicyViews.Revision> revisions(UUID policyId, Pageable pageable);
    Page<CommercialPolicyViews.History> history(UUID policyId, Pageable pageable);
    Page<CommercialPolicyViews.Activation> activations(UUID policyId, Pageable pageable);
    CommercialPolicyViews.ActivationAudience activationAudience(
            UUID policyId, UUID activationId, Pageable pageable);
    CommercialPolicyViews.AudiencePreview audience(UUID policyId, int page, int size);
    CommercialPolicyViews.ActivationPreview previewActivation(UUID policyId);
    CommercialPolicyViews.Detail activate(UUID policyId, CommercialPolicyRequests.Activation request);
    CommercialPolicyViews.Detail resume(UUID policyId, CommercialPolicyRequests.Activation request);
    CommercialPolicyViews.Detail pause(UUID policyId, CommercialPolicyRequests.VersionReason request);
    CommercialPolicyViews.Detail end(UUID policyId, CommercialPolicyRequests.VersionReason request);
    CommercialPolicyViews.Detail archive(UUID policyId, CommercialPolicyRequests.VersionReason request);
    void deleteDraft(UUID policyId, CommercialPolicyRequests.VersionReason request);
    CommercialPolicyViews.Owner owner(UUID policyId);
    CommercialPolicyViews.Detail reassignOwner(UUID policyId, CommercialPolicyRequests.ReassignOwner request);
    Page<AccountDirectoryEntryDto> chooseAccounts(String query, Boolean active, Pageable pageable);
    List<AccountDirectoryEntryDto> resolveAccountChoices(Collection<UUID> ids);
    Page<CommercialPolicyViews.SegmentChoice> chooseSegments(String query, Pageable pageable);
    List<CommercialPolicyViews.SegmentChoice> resolveSegmentChoices(Collection<String> references);
}
