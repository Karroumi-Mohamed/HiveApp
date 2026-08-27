package com.hiveapp.platform.client.plan.api;

import com.hiveapp.platform.client.plan.domain.constant.CommercialSegmentKind;
import com.hiveapp.platform.client.plan.domain.constant.CommercialSegmentSource;
import com.hiveapp.platform.client.plan.domain.constant.CommercialSegmentStatus;
import com.hiveapp.platform.client.plan.dto.CommercialSegmentRequests;
import com.hiveapp.platform.client.plan.dto.CommercialSegmentViews;
import com.hiveapp.platform.client.plan.service.CommercialSegmentAdminService;
import com.hiveapp.shared.api.PageResponse;
import com.hiveapp.shared.exception.InvalidRequestException;
import com.hiveapp.shared.exception.StaleActivationPreviewException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/segments")
@RequiredArgsConstructor
public class CommercialSegmentAdminController {

    private static final Map<String, String> SORTABLE = Map.of(
            "createdAt", "createdAt",
            "updatedAt", "updatedAt",
            "code", "code",
            "name", "name",
            "status", "status",
            "kind", "kind",
            "source", "source",
            "revisionNumber", "revisionNumber");

    private final CommercialSegmentAdminService service;

    @GetMapping
    public PageResponse<CommercialSegmentViews.Summary> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) CommercialSegmentStatus status,
            @RequestParam(required = false) CommercialSegmentKind kind,
            @RequestParam(required = false) CommercialSegmentSource source,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        return PageResponse.from(service.list(search, status, kind, source, includeArchived,
                CommercialProductPageRequest.of(page, size, sort, direction, SORTABLE,
                        "createdAt", Sort.Direction.DESC)));
    }

    @GetMapping("/{segmentId}")
    public CommercialSegmentViews.Detail get(@PathVariable UUID segmentId) {
        return service.get(segmentId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommercialSegmentViews.Detail create(
            @Valid @RequestBody CommercialSegmentRequests.Create request) {
        return service.create(request);
    }

    @PutMapping("/{segmentId}")
    public CommercialSegmentViews.Detail update(
            @PathVariable UUID segmentId,
            @Valid @RequestBody CommercialSegmentRequests.Update request) {
        return service.update(segmentId, request);
    }

    @PostMapping("/{segmentId}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public CommercialSegmentViews.Detail duplicate(
            @PathVariable UUID segmentId,
            @Valid @RequestBody CommercialSegmentRequests.Duplicate request) {
        return service.duplicate(segmentId, request);
    }

    @PostMapping("/{segmentId}/revisions")
    @ResponseStatus(HttpStatus.CREATED)
    public CommercialSegmentViews.Detail revise(
            @PathVariable UUID segmentId,
            @Valid @RequestBody CommercialSegmentRequests.VersionReason request) {
        return service.revise(segmentId, request);
    }

    @GetMapping("/{segmentId}/revisions")
    public PageResponse<CommercialSegmentViews.Revision> revisions(
            @PathVariable UUID segmentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(service.revisions(segmentId, bounded(page, size)));
    }

    @GetMapping("/{segmentId}/compare/{comparedSegmentId}")
    public CommercialSegmentViews.Comparison compare(
            @PathVariable UUID segmentId,
            @PathVariable UUID comparedSegmentId) {
        return service.compare(segmentId, comparedSegmentId);
    }

    @GetMapping("/{segmentId}/history")
    public PageResponse<CommercialSegmentViews.History> history(
            @PathVariable UUID segmentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(service.history(segmentId, bounded(page, size)));
    }

    @GetMapping("/{segmentId}/preview")
    public CommercialSegmentViews.Preview preview(@PathVariable UUID segmentId) {
        return service.preview(segmentId);
    }

    @GetMapping("/{segmentId}/preview-identities")
    public CommercialSegmentViews.IdentitySample previewIdentities(@PathVariable UUID segmentId) {
        return service.previewIdentities(segmentId);
    }

    @PostMapping("/{segmentId}/activate")
    public CommercialSegmentViews.Detail activate(
            @PathVariable UUID segmentId,
            @Valid @RequestBody CommercialSegmentRequests.Activation request) {
        try {
            return service.activate(segmentId, request);
        } catch (CannotAcquireLockException exception) {
            throw new StaleActivationPreviewException();
        }
    }

    @PostMapping("/{segmentId}/archive")
    public CommercialSegmentViews.Detail archive(
            @PathVariable UUID segmentId,
            @Valid @RequestBody CommercialSegmentRequests.VersionReason request) {
        return service.archive(segmentId, request);
    }

    @DeleteMapping("/{segmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable UUID segmentId,
            @Valid @RequestBody CommercialSegmentRequests.VersionReason request) {
        service.deleteDraft(segmentId, request);
    }

    @GetMapping("/{segmentId}/activations")
    public PageResponse<CommercialSegmentViews.Activation> activations(
            @PathVariable UUID segmentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(service.activations(segmentId, bounded(page, size)));
    }

    @GetMapping("/{segmentId}/activations/{activationId}/accounts")
    public CommercialSegmentViews.ActivationAudience activationAudience(
            @PathVariable UUID segmentId,
            @PathVariable UUID activationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.activationAudience(segmentId, activationId, bounded(page, size));
    }

    @GetMapping("/{segmentId}/activations/{activationId}/identities")
    public CommercialSegmentViews.ActivationIdentityAudience activationIdentities(
            @PathVariable UUID segmentId,
            @PathVariable UUID activationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.activationIdentities(segmentId, activationId, bounded(page, size));
    }

    @GetMapping("/{segmentId}/owner")
    public CommercialSegmentViews.Owner owner(@PathVariable UUID segmentId) {
        return service.owner(segmentId);
    }

    @PutMapping("/{segmentId}/owner")
    public CommercialSegmentViews.Detail reassignOwner(
            @PathVariable UUID segmentId,
            @Valid @RequestBody CommercialSegmentRequests.ReassignOwner request) {
        return service.reassignOwner(segmentId, request);
    }

    private PageRequest bounded(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new InvalidRequestException(
                    "Page must be non-negative and size must be between 1 and 100.");
        }
        return PageRequest.of(page, size);
    }
}
