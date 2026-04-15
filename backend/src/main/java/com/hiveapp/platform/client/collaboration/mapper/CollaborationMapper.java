package com.hiveapp.platform.client.collaboration.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.hiveapp.platform.client.collaboration.domain.entity.Collaboration;
import com.hiveapp.platform.client.collaboration.dto.CollaborationDto;

@Mapper(componentModel = "spring")
public interface CollaborationMapper {
    @Mapping(source = "clientAccount.id", target = "clientAccountId")
    @Mapping(source = "providerAccount.id", target = "providerAccountId")
    @Mapping(source = "company.id", target = "companyId")
    CollaborationDto toDto(Collaboration collaboration);
}
