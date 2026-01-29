package com.hiveapp.module.domain.service;

import com.hiveapp.module.domain.dto.*;
import com.hiveapp.module.domain.entity.Feature;
import com.hiveapp.module.domain.entity.Module;
import com.hiveapp.module.domain.mapper.ModuleMapper;
import com.hiveapp.module.domain.repository.FeatureRepository;
import com.hiveapp.module.domain.repository.ModuleRepository;
import com.hiveapp.shared.exception.DuplicateResourceException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ModuleService {

    private final ModuleRepository moduleRepository;
    private final FeatureRepository featureRepository;
    private final ModuleMapper moduleMapper;

    @Transactional
    public ModuleResponse createModule(CreateModuleRequest request) {
        if (moduleRepository.existsByCode(request.getCode())) {
            throw new DuplicateResourceException("Module", "code", request.getCode());
        }

        Module module = moduleMapper.toEntity(request);
        if (request.getSortOrder() != null) {
            module.setSortOrder(request.getSortOrder());
        }

        Module saved = moduleRepository.save(module);
        log.info("Module created: {} ({})", saved.getName(), saved.getCode());
        return moduleMapper.toResponse(saved);
    }

    @Transactional
    public FeatureResponse createFeature(CreateFeatureRequest request) {
        if (featureRepository.existsByCode(request.getCode())) {
            throw new DuplicateResourceException("Feature", "code", request.getCode());
        }

        Module module = moduleRepository.findById(request.getModuleId())
                .orElseThrow(() -> new ResourceNotFoundException("Module", "id", request.getModuleId()));

        Feature feature = moduleMapper.toFeatureEntity(request);
        if (request.getSortOrder() != null) {
            feature.setSortOrder(request.getSortOrder());
        }
        module.addFeature(feature);

        Feature saved = featureRepository.save(feature);
        log.info("Feature created: {} ({}) in module {}", saved.getName(), saved.getCode(), module.getCode());
        return moduleMapper.toFeatureResponse(saved);
    }

    public List<ModuleResponse> getAllActiveModules() {
        return moduleMapper.toResponseList(moduleRepository.findAllActiveWithFeatures());
    }

    public ModuleResponse getModuleById(UUID id) {
        Module module = moduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Module", "id", id));
        return moduleMapper.toResponse(module);
    }

    public ModuleResponse getModuleByCode(String code) {
        Module module = moduleRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Module", "code", code));
        return moduleMapper.toResponse(module);
    }

    public List<FeatureResponse> getFeaturesByModuleId(UUID moduleId) {
        List<Feature> features = featureRepository.findByModuleIdAndIsActiveTrue(moduleId);
        return features.stream().map(moduleMapper::toFeatureResponse).toList();
    }

    public List<Feature> getFeaturesByIds(Set<UUID> ids) {
        return featureRepository.findByIdsWithModule(ids);
    }

    public Feature findFeatureOrThrow(UUID id) {
        return featureRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Feature", "id", id));
    }

    public Module findModuleOrThrow(UUID id) {
        return moduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Module", "id", id));
    }

    public Set<UUID> getFeatureIdsByModuleId(UUID moduleId) {
        return featureRepository.findFeatureIdsByModuleId(moduleId);
    }
}
