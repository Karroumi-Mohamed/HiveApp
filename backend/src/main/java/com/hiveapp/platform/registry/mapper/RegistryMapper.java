package com.hiveapp.platform.registry.mapper;

import org.mapstruct.Mapper;
import com.hiveapp.platform.registry.domain.entity.Feature;
import com.hiveapp.platform.registry.domain.entity.Module;
import com.hiveapp.platform.registry.dto.FeatureDto;
import com.hiveapp.platform.registry.dto.ModuleDto;

@Mapper(componentModel = "spring")
public interface RegistryMapper {
    FeatureDto toFeatureDto(Feature feature);
    ModuleDto toModuleDto(Module module);
}
