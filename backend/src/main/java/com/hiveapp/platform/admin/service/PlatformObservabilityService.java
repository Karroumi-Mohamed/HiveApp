package com.hiveapp.platform.admin.service;

import com.hiveapp.platform.admin.dto.PlatformObservabilityModels;

public interface PlatformObservabilityService {
    PlatformObservabilityModels.Health health();

    PlatformObservabilityModels.Backlogs backlogs();

    PlatformObservabilityModels.LogAccess logAccess();
}
