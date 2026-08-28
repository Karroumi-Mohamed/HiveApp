package com.hiveapp.platform.client.plan.service;

import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "hiveapp.offers.codes")
public record CommercialOfferCodeProperties(@Size(min = 32) String pepper) {}
