package com.hiveapp.shared.security.context;

import com.hiveapp.shared.security.HiveAppUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ContextDetectionFilter extends OncePerRequestFilter {

    private final SecurityContextService securityContextService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String companyIdHeader = request.getHeader("X-Company-ID");
            String isB2BHeader = request.getHeader("X-Is-B2B");

            UUID userId = null;
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof HiveAppUserDetails details) {
                userId = details.getUserId();
            }

            if (userId != null) {
                HiveAppPermissionContext context = securityContextService.validateAndBuild(userId, companyIdHeader, isB2BHeader);
                HiveAppContextHolder.setContext(context);
            }

            filterChain.doFilter(request, response);
        } catch (Exception e) {
            // In a filter, we must manually trigger the error response or rethrow for GlobalExceptionHandler
            throw e;
        } finally {
            HiveAppContextHolder.clearContext();
        }
    }
}
