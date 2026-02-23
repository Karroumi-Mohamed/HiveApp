package com.hiveapp.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "hiveapp.jwt")
public class JwtProperties {
    private String secret;
    private long accessTokenExpiration;
    private long refreshTockenExpiration;
}
