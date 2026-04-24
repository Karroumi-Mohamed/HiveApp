package com.hiveapp.platform.client.role.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import com.hiveapp.platform.client.role.domain.entity.Role;
import com.hiveapp.platform.client.role.domain.entity.RolePermission;
import com.hiveapp.platform.client.role.dto.RoleDto;

import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface RoleMapper {
    
    @Mapping(source = "permissions", target = "permissionCodes", qualifiedByName = "mapPermissionsToCodes")
    @Mapping(source = "systemRole", target = "isSystemRole")
    RoleDto toDto(Role role);

    @Named("mapPermissionsToCodes")
    default List<String> mapPermissionsToCodes(List<RolePermission> permissions) {
        if (permissions == null) {
            return List.of();
        }
        return permissions.stream()
                .map(rp -> rp.getPermission().getCode())
                .collect(Collectors.toList());
    }
}
