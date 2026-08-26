package com.hiveapp.platform.client.plan.api;

import com.hiveapp.platform.client.plan.dto.PlanDto;
import com.hiveapp.platform.client.plan.service.CommercialCatalogResolver;
import com.hiveapp.platform.client.plan.service.PlanAdminReadModels;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/plans")
@RequiredArgsConstructor
public class PlanController {

    private final CommercialCatalogResolver commercialCatalogResolver;
    private final PlanAdminReadModels readModels;

    @GetMapping
    public List<PlanDto> listActivePlans() {
        return commercialCatalogResolver.resolveCatalog(
                        CommercialCatalogResolver.Audience.CLIENT_CATALOG).plans().stream()
                .filter(CommercialCatalogResolver.PlanResolution::clientVisible)
                .map(result -> readModels.toDto(result.plan()))
                .toList();
    }
}
