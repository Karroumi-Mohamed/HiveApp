package com.hiveapp.platform.client.company.mapper;

import org.mapstruct.Mapper;
import com.hiveapp.platform.client.account.domain.entity.Company;
import com.hiveapp.platform.client.company.dto.CompanyDto;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CompanyMapper {
    @Mapping(source = "active", target = "isActive")
    CompanyDto toDto(Company company);
}
