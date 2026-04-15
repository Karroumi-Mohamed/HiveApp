package com.hiveapp.platform.client.plan.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.dto.SubscriptionDto;

@Mapper(componentModel = "spring")
public interface SubscriptionMapper {
    @Mapping(source = "plan.code", target = "plan.code")
    @Mapping(source = "plan.name", target = "plan.name")
    SubscriptionDto toDto(Subscription subscription);
}
