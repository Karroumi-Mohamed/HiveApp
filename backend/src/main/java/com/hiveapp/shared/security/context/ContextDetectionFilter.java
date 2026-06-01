package com.hiveapp.shared.security.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiveapp.shared.exception.ApiError;
import com.hiveapp.shared.exception.ForbiddenException;
import com.hiveapp.shared.exception.InvalidRequestException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import com.hiveapp.shared.exception.UnauthorizedException;
import com.hiveapp.shared.security.HiveAppUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ContextDetectionFilter extends OncePerRequestFilter {

    private final SecurityContextService securityContextService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
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
        } catch (UnauthorizedException e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), ApiError.of(401, "Unauthorized", e.getMessage()));
        } catch (ForbiddenException e) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), ApiError.of(403, "Forbidden", e.getMessage()));
        } catch (InvalidRequestException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), ApiError.of(400, "Bad Request", e.getMessage()));
        } catch (ResourceNotFoundException e) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), ApiError.of(404, "Not Found", e.getMessage()));
        } finally {
            HiveAppContextHolder.clearContext();
        }
    }
}
