package com.hiveapp.permission.domain.service;

import com.hiveapp.permission.domain.dto.CreatePermissionRequest;
import com.hiveapp.permission.domain.dto.PermissionResponse;
import com.hiveapp.permission.domain.entity.Permission;
import com.hiveapp.permission.domain.mapper.PermissionMapper;
import com.hiveapp.permission.domain.repository.PermissionRepository;
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
public class PermissionService {

    private final PermissionRepository permissionRepository;
    private final PermissionMapper permissionMapper;

    @Transactional
    public PermissionResponse createPermission(CreatePermissionRequest request) {
        if (permissionRepository.existsByCode(request.getCode())) {
            throw new DuplicateResourceException("Permission", "code", request.getCode());
        }

        Permission permission = permissionMapper.toEntity(request);
        Permission saved = permissionRepository.save(permission);
        log.info("Permission created: {} ({})", saved.getName(), saved.getCode());
        return permissionMapper.toResponse(saved);
    }

    public PermissionResponse getPermissionById(UUID id) {
        Permission permission = findPermissionOrThrow(id);
        return permissionMapper.toResponse(permission);
    }

    public List<PermissionResponse> getPermissionsByFeatureId(UUID featureId) {
        return permissionMapper.toResponseList(permissionRepository.findByFeatureId(featureId));
    }

    public List<Permission> getPermissionsByFeatureIds(Set<UUID> featureIds) {
        return permissionRepository.findByFeatureIds(featureIds);
    }

    public List<Permission> getPermissionsByIds(Set<UUID> ids) {
        return permissionRepository.findByIds(ids);
    }

    public Permission findPermissionOrThrow(UUID id) {
        return permissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Permission", "id", id));
    }

    public Permission findPermissionByCodeOrThrow(String code) {
        return permissionRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Permission", "code", code));
    }
}
