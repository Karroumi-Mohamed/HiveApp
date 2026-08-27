package com.hiveapp.testsupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiveapp.platform.client.company.dto.CreateCompanyRequest;
import com.hiveapp.identity.dto.LoginRequest;
import com.hiveapp.identity.dto.RegisterRequest;
import com.hiveapp.platform.client.member.dto.CreateMemberRequest;
import com.hiveapp.platform.registry.service.RegistryCatalogVersionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class PlatformShellIntegrationTestSupport {

    protected static final String ADMIN_EMAIL = "test-admin@hiveapp.local";
    protected static final String ADMIN_PASSWORD = "test-only-admin-password";
    protected static final String CLIENT_PASSWORD = "password123";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected RegistryCatalogVersionService registryCatalogVersionService;

    protected String registerClientAndGetToken() throws Exception {
        String email = "client-" + UUID.randomUUID() + "@example.com";
        RegisterRequest request = new RegisterRequest(
                email,
                CLIENT_PASSWORD,
                "Client",
                "User",
                null
        );

        return accessToken(mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))));
    }

    protected String loginAdminAndGetToken() throws Exception {
        LoginRequest request = new LoginRequest(ADMIN_EMAIL, ADMIN_PASSWORD);
        return accessToken(mockMvc.perform(post("/api/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))));
    }

    protected String bearer(String token) {
        return "Bearer " + token;
    }

    protected JsonNode createCompany(String token, String name) throws Exception {
        CreateCompanyRequest request = new CreateCompanyRequest(
                name,
                name + " LLC",
                null,
                "Software",
                "US",
                null
        );

        String response = mockMvc.perform(post("/api/v1/companies")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response);
    }

    protected JsonNode listMembers(String token) throws Exception {
        String response = mockMvc.perform(get("/api/v1/members")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response);
    }

    protected UUID currentMemberId(String token) throws Exception {
        JsonNode members = listMembers(token);
        return UUID.fromString(members.get(0).get("id").asText());
    }

    protected UUID createOrdinaryMember(String token) throws Exception {
        CreateMemberRequest request = new CreateMemberRequest(
                "member-" + UUID.randomUUID().toString().substring(0, 8), null,
                "Created", "Member", "Created Member", null, null, List.of());
        String response = mockMvc.perform(post("/api/v1/members")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("member").get("id").asText());
    }

    protected String fetchPlanActivationToken(String adminToken, UUID planId) throws Exception {
        return fetchActivationToken(adminToken, "/api/admin/plans/{id}/activation-preview", planId);
    }

    protected String fetchAddOnActivationToken(String adminToken, UUID addOnId) throws Exception {
        return fetchActivationToken(adminToken, "/api/admin/add-ons/{id}/activation-preview", addOnId);
    }

    protected String fetchQuotaPackageActivationToken(String adminToken, UUID quotaPackageId)
            throws Exception {
        return fetchActivationToken(
                adminToken, "/api/admin/quota-packages/{id}/activation-preview", quotaPackageId);
    }

    private String fetchActivationToken(String adminToken, String path, UUID id) throws Exception {
        String response = mockMvc.perform(get(path, id)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("previewToken").asText();
    }

    private String accessToken(ResultActions resultActions) throws Exception {
        String response = resultActions
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return json.get("accessToken").asText();
    }
}
