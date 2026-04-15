package com.hiveapp.platform.client.company.mapper;

import org.mapstruct.Mapper;
import com.hiveapp.platform.client.account.domain.entity.Company;
import com.hiveapp.platform.client.company.dto.CompanyDto;

@Mapper(componentModel = "spring")
public interface CompanyMapper {
    CompanyDto toDto(Company company);
}
