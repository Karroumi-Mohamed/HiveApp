package com.hiveapp.shared.security.context;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;

@Component
public class ContextDetectionFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String companyIdHeader = request.getHeader("X-Company-ID");
            String accountIdHeader = request.getHeader("X-Account-ID");
            String isB2BHeader = request.getHeader("X-Is-B2B");

            if (accountIdHeader != null) {
                UUID accountId = UUID.fromString(accountIdHeader);
                UUID companyId = companyIdHeader != null ? UUID.fromString(companyIdHeader) : null;
                boolean isB2b = Boolean.parseBoolean(isB2BHeader);

                // TODO: Normally the Actor UserId comes from Spring Security Context
                // Here we simplify by passing a null or deriving it later
                HiveAppContextHolder.setContext(new HiveAppPermissionContext(
                        null, accountId, companyId, isB2b));
            }

            filterChain.doFilter(request, response);
        } finally {
            HiveAppContextHolder.clearContext();
        }
    }
}
