package com.hiveapp.role.domain.mapper;

import com.hiveapp.role.domain.dto.RoleResponse;
import com.hiveapp.role.domain.entity.Role;
import com.hiveapp.role.domain.entity.RolePermission;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Mapper(componentModel = "spring")
public interface RoleMapper {

    @Mapping(target = "systemRole", source = "systemRole")
    @Mapping(target = "active", source = "active")
    @Mapping(target = "permissionIds", source = "rolePermissions", qualifiedByName = "toPermissionIds")
    RoleResponse toResponse(Role role);

    List<RoleResponse> toResponseList(List<Role> roles);

    @Named("toPermissionIds")
    default List<UUID> toPermissionIds(List<RolePermission> rolePermissions) {
        if (rolePermissions == null) return Collections.emptyList();
        return rolePermissions.stream()
                .map(RolePermission::getPermissionId)
                .toList();
    }
}
