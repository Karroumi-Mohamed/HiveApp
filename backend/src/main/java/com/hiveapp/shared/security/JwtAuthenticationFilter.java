package com.hiveapp.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import org.springframework.lang.NonNull;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;
    private final AccessDeniedHandler accessDeniedHandler;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return requestPath(request).startsWith("/api/admin");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String token = extractToken(request);

        if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
            try {
                var claims = jwtTokenProvider.getClaimsFromToken(token);
                if (!jwtTokenProvider.hasPurpose(claims, TokenAudience.CLIENT, TokenUse.ACCESS)) {
                    log.warn("Rejected token with type {} and use {} on client endpoint",
                            claims.get("tokenType"), claims.get("tokenUse"));
                    if (!isPublicPath(request)) {
                        accessDeniedHandler.handle(request, response,
                                new AccessDeniedException("Token is not valid for the client API surface"));
                        return;
                    }
                } else {
                    String userId = jwtTokenProvider.getUserIdFromToken(token).toString();
                    var userDetails = userDetailsService.loadUserByUsername(userId);

                    var authentication = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities()
                    );

                    authentication.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception ex) {
                log.warn("Could not set user authentication: {}", ex.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private String requestPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && requestUri.startsWith(contextPath)) {
            return requestUri.substring(contextPath.length());
        }
        return requestUri;
    }

    private boolean isPublicPath(HttpServletRequest request) {
        String path = requestPath(request);
        return path.startsWith("/api/v1/auth/")
                || path.equals("/api/v1/features/catalog")
                || path.equals("/api/v1/invitations/validate")
                || path.equals("/api/v1/invitations/accept")
                || path.startsWith("/swagger-ui/")
                || path.equals("/swagger-ui.html")
                || path.startsWith("/v3/api-docs/")
                || path.equals("/actuator/health");
    }
}
