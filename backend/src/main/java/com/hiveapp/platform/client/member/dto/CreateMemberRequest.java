package com.hiveapp.platform.client.member.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateMemberRequest(
        @NotBlank
        @Size(min = 3, max = 50)
        @Pattern(regexp = "[A-Za-z0-9._-]+", message = "must contain only letters, numbers, dots, underscores, or hyphens")
        String username,
        @Email String email,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Size(max = 200) String displayName,
        @Size(max = 50) String phone,
        @Size(max = 80) String employeeNumber,
        List<@Valid InitialRoleAssignmentRequest> initialRoles
) {
    public CreateMemberRequest {
        initialRoles = initialRoles == null ? List.of() : List.copyOf(initialRoles);
    }
}
