package com.hiveapp.shared.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@RequiredArgsConstructor
public class CacheConfig {

    public static final String PERMISSION_CACHE = "permissions";
    public static final String PLAN_CEILING_CACHE = "planCeilings";
    public static final String COLLABORATION_CEILING_CACHE = "collaborationCeilings";

    private final PermissionCacheProperties cacheProperties;

    @Bean
    public CacheManager permissionCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                PERMISSION_CACHE,
                PLAN_CEILING_CACHE,
                COLLABORATION_CEILING_CACHE
        );
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(cacheProperties.getMaxSize())
                .expireAfterWrite(cacheProperties.getTtl(), TimeUnit.MILLISECONDS)
                .recordStats());
        return cacheManager;
    }
}
