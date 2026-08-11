package com.hiveapp.platform.client.plan.api;

import com.hiveapp.platform.client.plan.domain.constant.QuotaPackageStatus;
import com.hiveapp.platform.client.plan.dto.CreateQuotaPackageRequest;
import com.hiveapp.platform.client.plan.dto.QuotaPackageDto;
import com.hiveapp.platform.client.plan.dto.UpdateQuotaPackageRequest;
import com.hiveapp.platform.client.plan.service.PlanAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/quota-packages")
@RequiredArgsConstructor
public class QuotaPackageAdminController {

    private final PlanAdminService planAdminService;

    @GetMapping
    public List<QuotaPackageDto> list() {
        return planAdminService.listQuotaPackages();
    }

    @GetMapping("/{quotaPackageId}")
    public QuotaPackageDto get(@PathVariable UUID quotaPackageId) {
        return planAdminService.getQuotaPackage(quotaPackageId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public QuotaPackageDto create(@Valid @RequestBody CreateQuotaPackageRequest request) {
        return planAdminService.createQuotaPackage(request);
    }

    @PutMapping("/{quotaPackageId}")
    public QuotaPackageDto update(
            @PathVariable UUID quotaPackageId,
            @Valid @RequestBody UpdateQuotaPackageRequest request
    ) {
        return planAdminService.updateQuotaPackage(quotaPackageId, request);
    }

    @PatchMapping("/{quotaPackageId}/status")
    public QuotaPackageDto transitionStatus(
            @PathVariable UUID quotaPackageId,
            @RequestParam QuotaPackageStatus status
    ) {
        return planAdminService.transitionQuotaPackageStatus(quotaPackageId, status);
    }

    @DeleteMapping("/{quotaPackageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID quotaPackageId) {
        planAdminService.deleteQuotaPackage(quotaPackageId);
    }

}
