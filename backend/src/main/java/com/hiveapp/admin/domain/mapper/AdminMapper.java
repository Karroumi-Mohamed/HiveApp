package com.hiveapp.admin.domain.mapper;

import com.hiveapp.admin.domain.dto.*;
import com.hiveapp.admin.domain.entity.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.Collections;
import java.util.List;

@Mapper(componentModel = "spring")
public interface AdminMapper {

    @Mapping(target = "superAdmin", source = "superAdmin")
    @Mapping(target = "active", source = "active")
    @Mapping(target = "roles", source = "adminUserRoles", qualifiedByName = "toRoleResponses")
    AdminUserResponse toUserResponse(AdminUser adminUser);

    @Mapping(target = "active", source = "active")
    @Mapping(target = "permissions", source = "adminRolePermissions", qualifiedByName = "toPermissionResponses")
    AdminRoleResponse toRoleResponse(AdminRole adminRole);

    AdminPermissionResponse toPermissionResponse(AdminPermission adminPermission);

    @Named("toRoleResponses")
    default List<AdminRoleResponse> toRoleResponses(List<AdminUserRole> userRoles) {
        if (userRoles == null) return Collections.emptyList();
        return userRoles.stream()
                .map(ur -> toRoleResponse(ur.getAdminRole()))
                .toList();
    }

    @Named("toPermissionResponses")
    default List<AdminPermissionResponse> toPermissionResponses(List<AdminRolePermission> rolePermissions) {
        if (rolePermissions == null) return Collections.emptyList();
        return rolePermissions.stream()
                .map(rp -> toPermissionResponse(rp.getAdminPermission()))
                .toList();
    }
}
