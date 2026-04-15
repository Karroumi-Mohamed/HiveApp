package com.hiveapp.shared.security;

import java.util.List;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import dev.karroumi.permissionizer.PermissionGuard;
import dev.karroumi.permissionizer.PermissionPolicy;
import com.hiveapp.shared.security.policy.AdminPermissionPolicy;
import com.hiveapp.shared.security.policy.B2bCollaborationPolicy;
import com.hiveapp.shared.security.policy.PlanPolicy;
import com.hiveapp.shared.security.policy.UserRolePolicy;
import com.hiveapp.shared.security.context.ContextDetectionFilter;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;
    @org.springframework.beans.factory.annotation.Qualifier("adminUserDetailsService")
    private final UserDetailsService adminUserDetailsService;
    private final AuthEntryPoint authEntryPoint;
    private final AccessDeniedHandler accessDeniedHandler;
    
    private final AdminPermissionPolicy adminPermissionPolicy;
    private final B2bCollaborationPolicy b2bPolicy;
    private final PlanPolicy planPolicy;
    private final UserRolePolicy userRolePolicy;
    private final ContextDetectionFilter contextDetectionFilter;

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtTokenProvider, userDetailsService);
    }

    @Bean
    public AdminJwtAuthenticationFilter adminJwtAuthenticationFilter() {
        return new AdminJwtAuthenticationFilter(jwtTokenProvider, adminUserDetailsService);
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<AdminJwtAuthenticationFilter> adminJwtFilterRegistration(AdminJwtAuthenticationFilter filter) {
        FilterRegistrationBean<AdminJwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<ContextDetectionFilter> contextFilterRegistration(ContextDetectionFilter filter) {
        FilterRegistrationBean<ContextDetectionFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/v1/auth/**",
                    "/api/admin/auth/login",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**",
                    "/actuator/health"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex 
                .authenticationEntryPoint(authEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(adminJwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(contextDetectionFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider(){
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
        AuthenticationConfiguration configuration
    ) throws Exception {
        return configuration.getAuthenticationManager();
    }

    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @PostConstruct
    public void permissionsLoader() {
        PermissionGuard.builder()
            .addPolicy(adminPermissionPolicy)   // FIRST — short-circuits for admin actors
            .addPolicy(b2bPolicy)
            .addPolicy(planPolicy)
            .addPolicy(userRolePolicy)
            .addPolicy(PermissionPolicy.fromProvider(() -> {
                var auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth == null || !auth.isAuthenticated()) {
                    return List.of();
                }
                return auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .toList();
            }))
            .withAutoGuard()
            .skipVerification() 
            .initialize();
    }
}
