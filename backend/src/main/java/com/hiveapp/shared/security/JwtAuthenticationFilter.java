package com.hiveapp.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ACCOUNT_HEADER = "X-Account-Id";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;
    private final MemberContextResolver memberContextResolver;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            String jwt = extractJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && jwtTokenProvider.validateToken(jwt)) {
                UUID userId = jwtTokenProvider.getUserIdFromToken(jwt);
                UserDetails userDetails = userDetailsService.loadUserByUsername(userId.toString());

                // Populate member context for permission evaluation
                if (userDetails instanceof HiveAppUserDetails hiveDetails) {
                    populateMemberContext(hiveDetails, request, userId);
                }

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );

                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception ex) {
            log.error("Could not set user authentication in security context", ex);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Populate the member context on the user details for permission resolution.
     *
     * Strategy:
     * 1. If X-Account-Id header is present, look up the member for that specific account
     * 2. Otherwise, use the user's first (default) member — typically their own account
     *
     * This context is required for @PreAuthorize("hasPermission(...)") to work,
     * since HivePermissionEvaluator reads memberId and accountId from the principal.
     */
    private void populateMemberContext(HiveAppUserDetails userDetails, HttpServletRequest request, UUID userId) {
        try {
            String accountIdHeader = request.getHeader(ACCOUNT_HEADER);

            if (StringUtils.hasText(accountIdHeader)) {
                // Explicit account selection via header
                UUID accountId = UUID.fromString(accountIdHeader);
                memberContextResolver.resolve(userId, accountId)
                        .ifPresent(ctx -> {
                            userDetails.setMemberContext(ctx.getMemberId(), ctx.getAccountId());
                            log.debug("Member context set from header: memberId={}, accountId={}",
                                    ctx.getMemberId(), ctx.getAccountId());
                        });
            } else {
                // Default: use the user's first membership (their own account)
                memberContextResolver.resolveDefault(userId)
                        .ifPresent(ctx -> {
                            userDetails.setMemberContext(ctx.getMemberId(), ctx.getAccountId());
                            log.debug("Member context set from default: memberId={}, accountId={}",
                                    ctx.getMemberId(), ctx.getAccountId());
                        });
            }
        } catch (Exception e) {
            log.warn("Failed to populate member context for user {}: {}", userId, e.getMessage());
        }
    }

    private String extractJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/auth/")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/management/health");
    }
}
