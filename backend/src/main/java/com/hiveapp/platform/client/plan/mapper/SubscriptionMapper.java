package com.hiveapp.platform.client.plan.mapper;

import com.hiveapp.platform.client.plan.domain.entity.Subscription;
import com.hiveapp.platform.client.plan.dto.SubscriptionDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SubscriptionMapper {

    @Mapping(source = "plan.code",     target = "plan.code")
    @Mapping(source = "plan.name",     target = "plan.name")
    @Mapping(source = "plan.price",    target = "plan.basePrice")
    @Mapping(source = "currentPrice",  target = "currentPrice")
    SubscriptionDto toDto(Subscription subscription);
}
