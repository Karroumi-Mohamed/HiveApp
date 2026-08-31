package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.account.dto.AccountDirectoryEntryDto;
import com.hiveapp.platform.client.plan.domain.constant.CommercialCampaignAudienceMode;
import com.hiveapp.platform.client.plan.domain.constant.CommercialCampaignSource;
import com.hiveapp.platform.client.plan.domain.constant.CommercialCampaignStatus;
import com.hiveapp.platform.client.plan.dto.CommercialCampaignRequests;
import com.hiveapp.platform.client.plan.dto.CommercialCampaignViews;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CommercialCampaignAdminService {
    Page<CommercialCampaignViews.Summary> list(String search, CommercialCampaignStatus status,
                                               CommercialCampaignAudienceMode audienceMode,
                                               CommercialCampaignSource source,
                                               boolean includeArchived, Pageable pageable);
    CommercialCampaignViews.Detail get(UUID campaignId);
    CommercialCampaignViews.OperationState operations(UUID campaignId);
    CommercialCampaignViews.EditableDefinition editableDefinition(UUID campaignId);
    CommercialCampaignViews.Mutation create(CommercialCampaignRequests.Create request);
    CommercialCampaignViews.Mutation update(UUID campaignId, CommercialCampaignRequests.Update request);
    CommercialCampaignViews.Mutation duplicate(UUID campaignId, CommercialCampaignRequests.Duplicate request);
    CommercialCampaignViews.Mutation revise(UUID campaignId, CommercialCampaignRequests.VersionReason request);
    CommercialCampaignViews.Comparison compare(UUID campaignId, UUID comparedCampaignId);
    Page<CommercialCampaignViews.Revision> revisions(UUID campaignId, Pageable pageable);
    Page<CommercialCampaignViews.History> history(UUID campaignId, Pageable pageable);
    CommercialCampaignViews.AudiencePreview previewSchedule(UUID campaignId);
    CommercialCampaignViews.Mutation schedule(UUID campaignId, CommercialCampaignRequests.Schedule request);
    CommercialCampaignViews.Mutation pause(UUID campaignId, CommercialCampaignRequests.VersionReason request);
    CommercialCampaignViews.Mutation resume(UUID campaignId, CommercialCampaignRequests.VersionReason request);
    CommercialCampaignViews.Mutation end(UUID campaignId, CommercialCampaignRequests.VersionReason request);
    CommercialCampaignViews.Mutation archive(UUID campaignId, CommercialCampaignRequests.VersionReason request);
    void deleteDraft(UUID campaignId, CommercialCampaignRequests.VersionReason request);
    CommercialCampaignViews.Owner owner(UUID campaignId);
    CommercialCampaignViews.OwnerMutation reassignOwner(
            UUID campaignId, CommercialCampaignRequests.ReassignOwner request);
    Page<CommercialCampaignViews.OwnerChoice> chooseOwners(String query, Pageable pageable);
    List<CommercialCampaignViews.OwnerChoice> resolveOwnerChoices(Collection<UUID> ids);
    CommercialCampaignViews.FrozenAudience audience(UUID campaignId, Pageable pageable);
    CommercialCampaignViews.FrozenIdentityAudience audienceIdentities(UUID campaignId, Pageable pageable);
    Page<AccountDirectoryEntryDto> chooseAccounts(String query, Boolean active, Pageable pageable);
    List<AccountDirectoryEntryDto> resolveAccountChoices(Collection<UUID> ids);
    Page<CommercialCampaignViews.SegmentChoice> chooseSegments(String query, Pageable pageable);
    CommercialCampaignViews.SegmentChoice resolveSegmentChoice(UUID segmentId, UUID activationId);
}
