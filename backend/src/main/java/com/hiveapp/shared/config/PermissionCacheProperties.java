package com.hiveapp.shared.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "hiveapp.permission.cache")
public class PermissionCacheProperties {

    @Min(10000)
    private long ttl = 300000;

    @Min(100)
    private long maxSize = 10000;
}
