package com.hiveapp.platform.client.plan.api;

import com.hiveapp.platform.client.account.dto.AccountDirectoryEntryDto;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicySource;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyStatus;
import com.hiveapp.platform.client.plan.domain.constant.CommercialPolicyTargetKind;
import com.hiveapp.platform.client.plan.dto.CommercialPolicyRequests;
import com.hiveapp.platform.client.plan.dto.CommercialPolicyViews;
import com.hiveapp.platform.client.plan.service.CommercialPolicyAdminService;
import com.hiveapp.shared.api.PageResponse;
import com.hiveapp.shared.exception.InvalidRequestException;
import com.hiveapp.shared.exception.StaleActivationPreviewException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/commercial-policies")
@RequiredArgsConstructor
public class CommercialPolicyAdminController {

    private static final Map<String, String> SORTABLE = Map.ofEntries(
            Map.entry("createdAt", "createdAt"),
            Map.entry("updatedAt", "updatedAt"),
            Map.entry("code", "code"),
            Map.entry("name", "name"),
            Map.entry("status", "status"),
            Map.entry("targetKind", "targetKind"),
            Map.entry("source", "source"),
            Map.entry("priority", "priority"),
            Map.entry("effectiveFrom", "effectiveFrom"),
            Map.entry("effectiveUntil", "effectiveUntil"),
            Map.entry("revisionNumber", "revisionNumber"));

    private final CommercialPolicyAdminService service;

    @GetMapping
    public PageResponse<CommercialPolicyViews.Summary> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) CommercialPolicyStatus status,
            @RequestParam(required = false) CommercialPolicyTargetKind targetKind,
            @RequestParam(required = false) CommercialPolicySource source,
            @RequestParam(required = false) Instant effectiveAt,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        return PageResponse.from(service.list(search, status, targetKind, source, effectiveAt,
                includeArchived, page(page, size, sort, direction)));
    }

    @GetMapping("/{policyId}")
    public CommercialPolicyViews.Detail get(@PathVariable UUID policyId) {
        return service.get(policyId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommercialPolicyViews.Detail create(
            @Valid @RequestBody CommercialPolicyRequests.Create request) {
        return service.create(request);
    }

    @PutMapping("/{policyId}")
    public CommercialPolicyViews.Detail update(
            @PathVariable UUID policyId,
            @Valid @RequestBody CommercialPolicyRequests.Update request) {
        return service.update(policyId, request);
    }

    @PostMapping("/{policyId}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public CommercialPolicyViews.Detail duplicate(
            @PathVariable UUID policyId,
            @Valid @RequestBody CommercialPolicyRequests.Duplicate request) {
        return service.duplicate(policyId, request);
    }

    @PostMapping("/{policyId}/revisions")
    @ResponseStatus(HttpStatus.CREATED)
    public CommercialPolicyViews.Detail revise(
            @PathVariable UUID policyId,
            @Valid @RequestBody CommercialPolicyRequests.VersionReason request) {
        return service.revise(policyId, request);
    }

    @GetMapping("/{policyId}/revisions")
    public PageResponse<CommercialPolicyViews.Revision> revisions(
            @PathVariable UUID policyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(service.revisions(policyId, historyPage(page, size)));
    }

    @GetMapping("/{policyId}/compare/{comparedPolicyId}")
    public CommercialPolicyViews.Comparison compare(
            @PathVariable UUID policyId,
            @PathVariable UUID comparedPolicyId) {
        return service.compare(policyId, comparedPolicyId);
    }

    @GetMapping("/{policyId}/history")
    public PageResponse<CommercialPolicyViews.History> history(
            @PathVariable UUID policyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(service.history(policyId, historyPage(page, size)));
    }

    @GetMapping("/{policyId}/activations")
    public PageResponse<CommercialPolicyViews.Activation> activations(
            @PathVariable UUID policyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(service.activations(policyId, historyPage(page, size)));
    }

    @GetMapping("/{policyId}/activations/{activationId}/accounts")
    public CommercialPolicyViews.ActivationAudience activationAudience(
            @PathVariable UUID policyId,
            @PathVariable UUID activationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.activationAudience(policyId, activationId, historyPage(page, size));
    }

    @GetMapping("/{policyId}/audience")
    public CommercialPolicyViews.AudiencePreview audience(
            @PathVariable UUID policyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.audience(policyId, page, size);
    }

    @GetMapping("/{policyId}/activation-preview")
    public CommercialPolicyViews.ActivationPreview activationPreview(@PathVariable UUID policyId) {
        return service.previewActivation(policyId);
    }

    @PostMapping("/{policyId}/activate")
    public CommercialPolicyViews.Detail activate(
            @PathVariable UUID policyId,
            @Valid @RequestBody CommercialPolicyRequests.Activation request) {
        return applyReviewedActivation(() -> service.activate(policyId, request));
    }

    @PostMapping("/{policyId}/resume")
    public CommercialPolicyViews.Detail resume(
            @PathVariable UUID policyId,
            @Valid @RequestBody CommercialPolicyRequests.Activation request) {
        return applyReviewedActivation(() -> service.resume(policyId, request));
    }

    @PostMapping("/{policyId}/pause")
    public CommercialPolicyViews.Detail pause(
            @PathVariable UUID policyId,
            @Valid @RequestBody CommercialPolicyRequests.VersionReason request) {
        return service.pause(policyId, request);
    }

    @PostMapping("/{policyId}/end")
    public CommercialPolicyViews.Detail end(
            @PathVariable UUID policyId,
            @Valid @RequestBody CommercialPolicyRequests.VersionReason request) {
        return service.end(policyId, request);
    }

    @PostMapping("/{policyId}/archive")
    public CommercialPolicyViews.Detail archive(
            @PathVariable UUID policyId,
            @Valid @RequestBody CommercialPolicyRequests.VersionReason request) {
        return service.archive(policyId, request);
    }

    @DeleteMapping("/{policyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable UUID policyId,
            @Valid @RequestBody CommercialPolicyRequests.VersionReason request) {
        service.deleteDraft(policyId, request);
    }

    @GetMapping("/{policyId}/owner")
    public CommercialPolicyViews.Owner owner(@PathVariable UUID policyId) {
        return service.owner(policyId);
    }

    @PutMapping("/{policyId}/owner")
    public CommercialPolicyViews.Detail reassignOwner(
            @PathVariable UUID policyId,
            @Valid @RequestBody CommercialPolicyRequests.ReassignOwner request) {
        return service.reassignOwner(policyId, request);
    }

    @GetMapping("/account-choices")
    public PageResponse<AccountDirectoryEntryDto> chooseAccounts(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(service.chooseAccounts(query, active,
                choicePage(page, size)));
    }

    @GetMapping("/account-choices/selected")
    public List<AccountDirectoryEntryDto> resolveAccountChoices(@RequestParam List<UUID> ids) {
        return service.resolveAccountChoices(ids);
    }

    @GetMapping("/segment-choices")
    public PageResponse<CommercialPolicyViews.SegmentChoice> chooseSegments(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(service.chooseSegments(query, choicePage(page, size)));
    }

    @GetMapping("/segment-choices/selected")
    public List<CommercialPolicyViews.SegmentChoice> resolveSegmentChoices(
            @RequestParam List<String> references) {
        return service.resolveSegmentChoices(references);
    }

    private PageRequest page(int page, int size, String sort, String direction) {
        return CommercialProductPageRequest.of(page, size, sort, direction, SORTABLE,
                "createdAt", Sort.Direction.DESC);
    }

    private PageRequest historyPage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new InvalidRequestException(
                    "Page must be non-negative and size must be between 1 and 100.");
        }
        return PageRequest.of(page, size);
    }

    private PageRequest choicePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new InvalidRequestException(
                    "Page must be non-negative and size must be between 1 and 100.");
        }
        return PageRequest.of(page, size,
                Sort.by(Sort.Direction.ASC, "name").and(Sort.by(Sort.Direction.ASC, "id")));
    }

    /** A concurrent reviewed apply is retryable, never an opaque infrastructure failure. */
    private CommercialPolicyViews.Detail applyReviewedActivation(
            java.util.function.Supplier<CommercialPolicyViews.Detail> operation) {
        try {
            return operation.get();
        } catch (CannotAcquireLockException exception) {
            throw new StaleActivationPreviewException();
        }
    }
}
