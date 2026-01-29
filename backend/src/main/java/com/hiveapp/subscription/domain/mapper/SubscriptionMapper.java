package com.hiveapp.subscription.domain.mapper;

import com.hiveapp.subscription.domain.dto.SubscriptionResponse;
import com.hiveapp.subscription.domain.entity.Subscription;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface SubscriptionMapper {

    SubscriptionResponse toResponse(Subscription subscription);

    List<SubscriptionResponse> toResponseList(List<Subscription> subscriptions);
}
