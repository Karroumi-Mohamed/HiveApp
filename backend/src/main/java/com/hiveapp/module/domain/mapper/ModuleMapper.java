package com.hiveapp.module.domain.mapper;

import com.hiveapp.module.domain.dto.*;
import com.hiveapp.module.domain.entity.Feature;
import com.hiveapp.module.domain.entity.Module;
import org.mapstruct.BeanMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ModuleMapper {

    @Mapping(target = "active", source = "active")
    ModuleResponse toResponse(Module module);

    List<ModuleResponse> toResponseList(List<Module> modules);

    @Mapping(target = "moduleId", source = "module.id")
    @Mapping(target = "moduleCode", source = "module.code")
    @Mapping(target = "active", source = "active")
    FeatureResponse toFeatureResponse(Feature feature);

    @BeanMapping(builder = @Builder(disableBuilder = true))
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "features", ignore = true)
    @Mapping(target = "active", constant = "true")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Module toEntity(CreateModuleRequest request);

    @BeanMapping(builder = @Builder(disableBuilder = true))
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "module", ignore = true)
    @Mapping(target = "active", constant = "true")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Feature toFeatureEntity(CreateFeatureRequest request);
}
