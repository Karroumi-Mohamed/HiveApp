package com.hiveapp.platform.admin.api;

import com.hiveapp.identity.domain.constant.CredentialTokenPurpose;
import com.hiveapp.platform.admin.dto.PlatformCommunicationModels;
import com.hiveapp.platform.admin.service.PlatformCommunicationService;
import com.hiveapp.shared.api.PageResponse;
import com.hiveapp.shared.email.delivery.EmailDeliveryStatus;
import com.hiveapp.shared.exception.InvalidRequestException;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/communications")
@RequiredArgsConstructor
public class PlatformCommunicationAdminController {
    private final PlatformCommunicationService communications;

    @GetMapping("/summary")
    public PlatformCommunicationModels.Summary summary() {
        return communications.summary();
    }

    @GetMapping
    public PageResponse<PlatformCommunicationModels.Delivery> search(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant until,
            @RequestParam(required = false) EmailDeliveryStatus status,
            @RequestParam(required = false) CredentialTokenPurpose purpose,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) UUID recipientUserId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw new InvalidRequestException("Communication pages support 1 to 100 rows.");
        }
        var query = new PlatformCommunicationService.Query(
                from, until, status, purpose, accountId, recipientUserId);
        var order = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        return PageResponse.from(communications.search(query, PageRequest.of(page, size, order)));
    }

    @GetMapping("/{id}")
    public PlatformCommunicationModels.Delivery detail(@PathVariable UUID id) {
        return communications.detail(id);
    }

    @GetMapping("/{id}/recipient")
    public PlatformCommunicationModels.RecipientIdentity recipientIdentity(@PathVariable UUID id) {
        return communications.recipientIdentity(id);
    }

    @GetMapping("/{id}/failure-evidence")
    public PlatformCommunicationModels.FailureEvidence failureEvidence(@PathVariable UUID id) {
        return communications.failureEvidence(id);
    }
}
