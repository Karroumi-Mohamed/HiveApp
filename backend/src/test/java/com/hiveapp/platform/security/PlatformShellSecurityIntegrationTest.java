package com.hiveapp.platform.security;

import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsStringIgnoringCase;

class PlatformShellSecurityIntegrationTest extends PlatformShellIntegrationTestSupport {

    @Test
    void clientWorkspaceEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void clientTokenCanUseClientWorkspaceEndpointThroughGuardedService() throws Exception {
        String clientToken = registerClientAndGetToken();

        mockMvc.perform(get("/api/v1/accounts/me")
                        .header("Authorization", bearer(clientToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Client's Workspace"));
    }

    @Test
    void adminTokenCanUseAdminEndpoint() throws Exception {
        String adminToken = loginAdminAndGetToken();

        mockMvc.perform(get("/api/admin/me")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(ADMIN_EMAIL));
    }

    @Test
    void clientTokenCannotUseAdminEndpoint() throws Exception {
        String clientToken = registerClientAndGetToken();

        mockMvc.perform(get("/api/admin/me")
                        .header("Authorization", bearer(clientToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You do not have permission to access this resource"));
    }

    @Test
    void adminTokenCannotUseClientWorkspaceEndpoint() throws Exception {
        String adminToken = loginAdminAndGetToken();

        mockMvc.perform(get("/api/v1/accounts/me")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You do not have permission to access this resource"));
    }

    @Test
    void invalidClientSurfaceTokenRemainsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/me")
                        .header("Authorization", bearer("not-a-jwt")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidAdminSurfaceTokenRemainsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/admin/me")
                        .header("Authorization", bearer("not-a-jwt")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void browserPreflightAllowsCompanyAndB2bContextHeaders() throws Exception {
        mockMvc.perform(options("/api/v1/companies/{id}", java.util.UUID.randomUUID())
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                "Authorization, X-Company-ID, X-Is-B2B"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        containsStringIgnoringCase("X-Company-ID")))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        containsStringIgnoringCase("X-Is-B2B")));
    }
}
