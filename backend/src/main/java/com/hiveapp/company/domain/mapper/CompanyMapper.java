package com.hiveapp.company.domain.mapper;

import com.hiveapp.company.domain.dto.CompanyModuleResponse;
import com.hiveapp.company.domain.dto.CompanyResponse;
import com.hiveapp.company.domain.dto.CreateCompanyRequest;
import com.hiveapp.company.domain.entity.Company;
import com.hiveapp.company.domain.entity.CompanyModule;
import org.mapstruct.BeanMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CompanyMapper {

    @Mapping(target = "active", source = "active")
    @Mapping(target = "activeModules", source = "companyModules")
    CompanyResponse toResponse(Company company);

    List<CompanyResponse> toResponseList(List<Company> companies);

    @Mapping(target = "active", source = "active")
    CompanyModuleResponse toModuleResponse(CompanyModule companyModule);

    @BeanMapping(builder = @Builder(disableBuilder = true))
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "logoUrl", ignore = true)
    @Mapping(target = "active", constant = "true")
    @Mapping(target = "companyModules", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Company toEntity(CreateCompanyRequest request);
}
