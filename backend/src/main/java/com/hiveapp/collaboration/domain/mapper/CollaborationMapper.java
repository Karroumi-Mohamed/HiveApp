package com.hiveapp.collaboration.domain.mapper;

import com.hiveapp.collaboration.domain.dto.CollaborationResponse;
import com.hiveapp.collaboration.domain.entity.Collaboration;
import com.hiveapp.collaboration.domain.entity.CollaborationPermission;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Mapper(componentModel = "spring")
public interface CollaborationMapper {

    @Mapping(target = "permissionIds", source = "collaborationPermissions", qualifiedByName = "toPermissionIds")
    CollaborationResponse toResponse(Collaboration collaboration);

    List<CollaborationResponse> toResponseList(List<Collaboration> collaborations);

    @Named("toPermissionIds")
    default List<UUID> toPermissionIds(List<CollaborationPermission> permissions) {
        if (permissions == null) return Collections.emptyList();
        return permissions.stream()
                .map(CollaborationPermission::getPermissionId)
                .toList();
    }
}
