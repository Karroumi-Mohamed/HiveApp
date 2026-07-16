package com.hiveapp.platform.client.company.mapper;

import org.mapstruct.Mapper;
import com.hiveapp.platform.client.account.domain.entity.Company;
import com.hiveapp.platform.client.company.dto.CompanyDto;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CompanyMapper {
    @Mapping(source = "active", target = "isActive")
    @Mapping(target = "warnings", expression = "java(java.util.List.of())")
    CompanyDto toDto(Company company);

    default CompanyDto toDto(Company company, List<String> warnings) {
        CompanyDto mapped = toDto(company);
        return new CompanyDto(
                mapped.id(), mapped.name(), mapped.legalName(), mapped.taxId(), mapped.industry(),
                mapped.country(), mapped.address(), mapped.logoUrl(), mapped.isActive(),
                warnings == null ? List.of() : List.copyOf(warnings));
    }
}
