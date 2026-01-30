package com.hiveapp.permission.domain.mapper;

import com.hiveapp.permission.domain.dto.CreatePermissionRequest;
import com.hiveapp.permission.domain.dto.PermissionResponse;
import com.hiveapp.permission.domain.entity.Permission;
import org.mapstruct.BeanMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PermissionMapper {

    PermissionResponse toResponse(Permission permission);

    List<PermissionResponse> toResponseList(List<Permission> permissions);

    @BeanMapping(builder = @Builder(disableBuilder = true))
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Permission toEntity(CreatePermissionRequest request);
}
