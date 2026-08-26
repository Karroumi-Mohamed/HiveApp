package com.hiveapp.platform.client.plan.service;

import com.hiveapp.platform.client.plan.domain.constant.AddOnStatus;
import com.hiveapp.platform.client.plan.domain.constant.PlanExtensionPolicy;
import com.hiveapp.platform.client.plan.domain.constant.PlanStatus;
import com.hiveapp.platform.client.plan.domain.constant.ProductSalesVisibility;
import com.hiveapp.platform.client.plan.domain.constant.QuotaPackageStatus;
import com.hiveapp.platform.client.plan.dto.AddOnChooserItemDto;
import com.hiveapp.platform.client.plan.dto.AddOnOperationalListItemDto;
import com.hiveapp.platform.client.plan.dto.PlanChooserItemDto;
import com.hiveapp.platform.client.plan.dto.PlanOperationalListItemDto;
import com.hiveapp.platform.client.plan.dto.QuotaPackageChooserItemDto;
import com.hiveapp.platform.client.plan.dto.QuotaPackageOperationalListItemDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;
import java.util.Collection;
import java.util.List;

public interface CommercialProductOperationsService {

    Page<PlanOperationalListItemDto> listPlans(
            String search, PlanStatus status, ProductSalesVisibility salesVisibility,
            PlanExtensionPolicy extensionPolicy, UUID lineageId, Pageable pageable);

    PlanOperationalListItemDto getPlanOperations(UUID planId);

    Page<AddOnOperationalListItemDto> listAddOns(
            String search, AddOnStatus status, ProductSalesVisibility salesVisibility,
            UUID lineageId, String featureCode, String targetPlanCode, Pageable pageable);

    AddOnOperationalListItemDto getAddOnOperations(UUID addOnId);

    Page<QuotaPackageOperationalListItemDto> listQuotaPackages(
            String search, QuotaPackageStatus status, ProductSalesVisibility salesVisibility,
            UUID lineageId, String featureCode, String resource, String targetPlanCode,
            String targetAddOnCode, Pageable pageable);

    QuotaPackageOperationalListItemDto getQuotaPackageOperations(UUID quotaPackageId);

    Page<PlanChooserItemDto> choosePlans(
            String search, ProductSalesVisibility salesVisibility, Pageable pageable);

    List<PlanChooserItemDto> resolvePlanChoices(Collection<UUID> ids);

    List<PlanChooserItemDto> resolvePlanChoicesByCode(Collection<String> codes);

    Page<AddOnChooserItemDto> chooseAddOns(
            String search, ProductSalesVisibility salesVisibility, String featureCode, Pageable pageable);

    List<AddOnChooserItemDto> resolveAddOnChoices(Collection<UUID> ids);

    List<AddOnChooserItemDto> resolveAddOnChoicesByCode(Collection<String> codes);

    Page<QuotaPackageChooserItemDto> chooseQuotaPackages(
            String search, ProductSalesVisibility salesVisibility, String featureCode,
            String resource, Pageable pageable);

    List<QuotaPackageChooserItemDto> resolveQuotaPackageChoices(Collection<UUID> ids);

    List<QuotaPackageChooserItemDto> resolveQuotaPackageChoicesByCode(Collection<String> codes);
}
