package com.hiveapp.plan.domain.mapper;

import com.hiveapp.plan.domain.dto.PlanFeatureResponse;
import com.hiveapp.plan.domain.dto.PlanResponse;
import com.hiveapp.plan.domain.entity.Plan;
import com.hiveapp.plan.domain.entity.PlanFeature;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PlanMapper {

    @Mapping(target = "active", source = "active")
    @Mapping(target = "features", source = "planFeatures")
    PlanResponse toResponse(Plan plan);

    List<PlanResponse> toResponseList(List<Plan> plans);

    PlanFeatureResponse toFeatureResponse(PlanFeature planFeature);
}
