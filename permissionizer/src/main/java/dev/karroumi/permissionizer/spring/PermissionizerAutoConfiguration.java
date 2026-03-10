package dev.karroumi.permissionizer.spring;

import dev.karroumi.permissionizer.PermissionGuard;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.logging.Logger;

/**
 * Spring Boot auto-configuration that registers the
 * {@link PermissionInterceptor} and signals to {@link PermissionGuard}
 * that a Spring-based enforcement mechanism is active.
 *
 * <p>
 * Discovered automatically via Spring Boot's auto-configuration
 * mechanism through the {@code AutoConfiguration.imports} file.
 * No manual import, annotation, or bean declaration needed in
 * the host application.
 * </p>
 *
 * <p>
 * If Spring AOP is not on the classpath, this configuration
 * class fails to load silently — the application starts normally
 * in manual mode or with Byte Buddy auto-guard.
 * </p>
 */
@Configuration
public class PermissionizerAutoConfiguration {

    private static final Logger LOG = Logger.getLogger(
            PermissionizerAutoConfiguration.class.getName());

    @Bean
    public PermissionInterceptor permissionInterceptor() {
        PermissionGuard.registerSpringInterceptor();
        LOG.info("[Permissionizer] Spring AOP interceptor registered");
        return new PermissionInterceptor();
    }
}
