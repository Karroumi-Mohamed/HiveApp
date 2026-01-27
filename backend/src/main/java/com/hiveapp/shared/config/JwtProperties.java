package com.hiveapp.shared.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "hiveapp.jwt")
public class JwtProperties {

    @NotBlank
    private String secret;

    @Min(60000)
    private long accessTokenExpiration = 900000;

    @Min(60000)
    private long refreshTokenExpiration = 604800000;

    @NotBlank
    private String issuer = "hiveapp-platform";
}
