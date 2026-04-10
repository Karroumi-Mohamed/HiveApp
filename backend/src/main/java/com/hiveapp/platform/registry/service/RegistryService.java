package com.hiveapp.platform.registry.service;

import com.hiveapp.platform.registry.domain.entity.Module;
import com.hiveapp.platform.registry.domain.entity.Feature;
import java.util.List;
import java.util.UUID;

public interface RegistryService {
    List<Module> getAllModules();
    Module createModule(String code, String name);
    Feature createFeature(UUID moduleId, String code, String name);
    void toggleModule(UUID id, boolean active);
}
