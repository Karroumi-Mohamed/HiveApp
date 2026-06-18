package com.hiveapp.platform.registry.api;

import com.hiveapp.platform.registry.dto.PublicFeatureCatalogModuleDto;
import com.hiveapp.platform.registry.service.PublicFeatureCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/features")
@RequiredArgsConstructor
public class PublicFeatureCatalogController {

    private final PublicFeatureCatalogService publicFeatureCatalogService;

    @GetMapping("/catalog")
    public List<PublicFeatureCatalogModuleDto> getCatalog() {
        return publicFeatureCatalogService.getPublicCatalog();
    }
}
