package com.hiveapp.platform.client.plan.api;

import com.hiveapp.platform.client.account.dto.AccountDirectoryEntryDto;
import com.hiveapp.platform.client.plan.domain.constant.CommercialCampaignAudienceMode;
import com.hiveapp.platform.client.plan.domain.constant.CommercialCampaignSource;
import com.hiveapp.platform.client.plan.domain.constant.CommercialCampaignStatus;
import com.hiveapp.platform.client.plan.dto.CommercialCampaignRequests;
import com.hiveapp.platform.client.plan.dto.CommercialCampaignViews;
import com.hiveapp.platform.client.plan.service.CommercialCampaignAdminService;
import com.hiveapp.shared.api.PageResponse;
import com.hiveapp.shared.exception.ApiError;
import com.hiveapp.shared.exception.ErrorCode;
import com.hiveapp.shared.exception.InvalidRequestException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/campaigns")
@RequiredArgsConstructor
public class CommercialCampaignAdminController {

    private static final Map<String, String> SORTABLE = Map.of(
            "createdAt", "createdAt", "updatedAt", "updatedAt", "code", "code",
            "name", "name", "status", "status", "source", "source",
            "startsAt", "startsAt", "endsAt", "endsAt", "revisionNumber", "revisionNumber");

    private final CommercialCampaignAdminService service;

    @GetMapping
    public PageResponse<CommercialCampaignViews.Summary> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) CommercialCampaignStatus status,
            @RequestParam(required = false) CommercialCampaignAudienceMode audienceMode,
            @RequestParam(required = false) CommercialCampaignSource source,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        return PageResponse.from(service.list(search, status, audienceMode, source, includeArchived,
                CommercialProductPageRequest.of(page, size, sort, direction, SORTABLE,
                        "createdAt", Sort.Direction.DESC)));
    }

    @GetMapping("/{campaignId}")
    public CommercialCampaignViews.Detail get(@PathVariable UUID campaignId) {
        return service.get(campaignId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommercialCampaignViews.Detail create(
            @Valid @RequestBody CommercialCampaignRequests.Create request) {
        return service.create(request);
    }

    @PutMapping("/{campaignId}")
    public CommercialCampaignViews.Detail update(@PathVariable UUID campaignId,
            @Valid @RequestBody CommercialCampaignRequests.Update request) {
        return service.update(campaignId, request);
    }

    @PostMapping("/{campaignId}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public CommercialCampaignViews.Detail duplicate(@PathVariable UUID campaignId,
            @Valid @RequestBody CommercialCampaignRequests.Duplicate request) {
        return service.duplicate(campaignId, request);
    }

    @PostMapping("/{campaignId}/revisions")
    @ResponseStatus(HttpStatus.CREATED)
    public CommercialCampaignViews.Detail revise(@PathVariable UUID campaignId,
            @Valid @RequestBody CommercialCampaignRequests.VersionReason request) {
        return service.revise(campaignId, request);
    }

    @GetMapping("/{campaignId}/revisions")
    public PageResponse<CommercialCampaignViews.Revision> revisions(@PathVariable UUID campaignId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(service.revisions(campaignId, bounded(page, size)));
    }

    @GetMapping("/{campaignId}/compare/{comparedCampaignId}")
    public CommercialCampaignViews.Comparison compare(@PathVariable UUID campaignId,
            @PathVariable UUID comparedCampaignId) {
        return service.compare(campaignId, comparedCampaignId);
    }

    @GetMapping("/{campaignId}/history")
    public PageResponse<CommercialCampaignViews.History> history(@PathVariable UUID campaignId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(service.history(campaignId, bounded(page, size)));
    }

    @GetMapping("/{campaignId}/schedule-preview")
    public CommercialCampaignViews.AudiencePreview previewSchedule(@PathVariable UUID campaignId) {
        return service.previewSchedule(campaignId);
    }

    @PostMapping("/{campaignId}/schedule")
    public CommercialCampaignViews.Detail schedule(@PathVariable UUID campaignId,
            @Valid @RequestBody CommercialCampaignRequests.Schedule request) {
        return service.schedule(campaignId, request);
    }

    @PostMapping("/{campaignId}/pause")
    public CommercialCampaignViews.Detail pause(@PathVariable UUID campaignId,
            @Valid @RequestBody CommercialCampaignRequests.VersionReason request) {
        return service.pause(campaignId, request);
    }

    @PostMapping("/{campaignId}/resume")
    public CommercialCampaignViews.Detail resume(@PathVariable UUID campaignId,
            @Valid @RequestBody CommercialCampaignRequests.VersionReason request) {
        return service.resume(campaignId, request);
    }

    @PostMapping("/{campaignId}/end")
    public CommercialCampaignViews.Detail end(@PathVariable UUID campaignId,
            @Valid @RequestBody CommercialCampaignRequests.VersionReason request) {
        return service.end(campaignId, request);
    }

    @PostMapping("/{campaignId}/archive")
    public CommercialCampaignViews.Detail archive(@PathVariable UUID campaignId,
            @Valid @RequestBody CommercialCampaignRequests.VersionReason request) {
        return service.archive(campaignId, request);
    }

    @DeleteMapping("/{campaignId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDraft(@PathVariable UUID campaignId,
            @Valid @RequestBody CommercialCampaignRequests.VersionReason request) {
        service.deleteDraft(campaignId, request);
    }

    @GetMapping("/{campaignId}/owner")
    public CommercialCampaignViews.Owner owner(@PathVariable UUID campaignId) {
        return service.owner(campaignId);
    }

    @PutMapping("/{campaignId}/owner")
    public CommercialCampaignViews.Detail reassignOwner(@PathVariable UUID campaignId,
            @Valid @RequestBody CommercialCampaignRequests.ReassignOwner request) {
        return service.reassignOwner(campaignId, request);
    }

    @GetMapping("/{campaignId}/audience")
    public CommercialCampaignViews.FrozenAudience audience(@PathVariable UUID campaignId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.audience(campaignId, bounded(page, size));
    }

    @GetMapping("/{campaignId}/audience-identities")
    public CommercialCampaignViews.FrozenIdentityAudience audienceIdentities(
            @PathVariable UUID campaignId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.audienceIdentities(campaignId, bounded(page, size));
    }

    @GetMapping("/account-choices")
    public PageResponse<AccountDirectoryEntryDto> chooseAccounts(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(service.chooseAccounts(query, active, choicePage(page, size)));
    }

    @GetMapping("/account-choices/selected")
    public List<AccountDirectoryEntryDto> resolveAccountChoices(@RequestParam List<UUID> ids) {
        return service.resolveAccountChoices(ids);
    }

    @GetMapping("/segment-choices")
    public PageResponse<CommercialCampaignViews.SegmentChoice> chooseSegments(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(service.chooseSegments(query, choicePage(page, size)));
    }

    @GetMapping("/segment-choices/selected")
    public CommercialCampaignViews.SegmentChoice resolveSegmentChoice(
            @RequestParam UUID segmentId,
            @RequestParam UUID activationId) {
        return service.resolveSegmentChoice(segmentId, activationId);
    }

    @GetMapping("/owner-choices")
    public PageResponse<CommercialCampaignViews.OwnerChoice> chooseOwners(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(service.chooseOwners(query, ownerChoicePage(page, size)));
    }

    @GetMapping("/owner-choices/selected")
    public List<CommercialCampaignViews.OwnerChoice> resolveOwnerChoices(
            @RequestParam List<UUID> ids) {
        return service.resolveOwnerChoices(ids);
    }

    private PageRequest bounded(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new InvalidRequestException("Page must be non-negative and size must be between 1 and 100.");
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")
                .and(Sort.by(Sort.Direction.ASC, "id")));
    }

    private PageRequest choicePage(int page, int size) {
        return bounded(page, size).withSort(Sort.by(Sort.Direction.ASC, "name")
                .and(Sort.by(Sort.Direction.ASC, "id")));
    }

    private PageRequest ownerChoicePage(int page, int size) {
        return bounded(page, size).withSort(Sort.by(Sort.Direction.ASC, "user.email")
                .and(Sort.by(Sort.Direction.ASC, "id")));
    }

    @ExceptionHandler(CannotAcquireLockException.class)
    public ResponseEntity<ApiError> handleLockConflict(CannotAcquireLockException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(
                HttpStatus.CONFLICT.value(), ErrorCode.STALE_RESOURCE_VERSION,
                "Conflict", "Campaign changed concurrently; reload and retry."));
    }
}
