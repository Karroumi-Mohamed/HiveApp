package com.hiveapp.platform.security;

import com.hiveapp.platform.client.member.dto.UpdateMemberRequest;
import com.hiveapp.testsupport.PlatformShellIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MemberIsolationIntegrationTest extends PlatformShellIntegrationTestSupport {

    @Test
    void memberListOnlyReturnsCurrentWorkspaceMembers() throws Exception {
        String ownerToken = registerClientAndGetToken();
        String otherToken = registerClientAndGetToken();
        UUID ownerMemberId = currentMemberId(ownerToken);
        UUID otherMemberId = currentMemberId(otherToken);

        mockMvc.perform(get("/api/v1/members")
                        .header("Authorization", bearer(otherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", everyItem(not(ownerMemberId.toString()))))
                .andExpect(jsonPath("$[0].id").value(otherMemberId.toString()));
    }

    @Test
    void clientCannotUpdateMemberFromAnotherWorkspace() throws Exception {
        String ownerToken = registerClientAndGetToken();
        String otherToken = registerClientAndGetToken();
        UUID ownerMemberId = currentMemberId(ownerToken);

        UpdateMemberRequest request = new UpdateMemberRequest("Changed");

        mockMvc.perform(patch("/api/v1/members/{id}", ownerMemberId)
                        .header("Authorization", bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void clientCannotDeleteMemberFromAnotherWorkspace() throws Exception {
        String ownerToken = registerClientAndGetToken();
        String otherToken = registerClientAndGetToken();
        UUID ownerMemberId = currentMemberId(ownerToken);

        mockMvc.perform(delete("/api/v1/members/{id}", ownerMemberId)
                        .header("Authorization", bearer(otherToken)))
                .andExpect(status().isForbidden());
    }
}
