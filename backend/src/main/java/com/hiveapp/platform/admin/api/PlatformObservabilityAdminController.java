package com.hiveapp.platform.admin.api;

import com.hiveapp.platform.admin.dto.PlatformObservabilityModels;
import com.hiveapp.platform.admin.service.PlatformObservabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/observability")
@RequiredArgsConstructor
public class PlatformObservabilityAdminController {
    private final PlatformObservabilityService observability;

    @GetMapping("/health")
    public PlatformObservabilityModels.Health health() {
        return observability.health();
    }

    @GetMapping("/backlogs")
    public PlatformObservabilityModels.Backlogs backlogs() {
        return observability.backlogs();
    }

    @GetMapping("/log-access")
    public PlatformObservabilityModels.LogAccess logAccess() {
        return observability.logAccess();
    }
}
