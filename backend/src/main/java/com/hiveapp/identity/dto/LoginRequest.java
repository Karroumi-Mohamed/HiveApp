package com.hiveapp.identity.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @JsonAlias("email") String identifier,
        @NotBlank String password,
        String accountCode,
        String employeeNumber
) {
    public LoginRequest(String identifier, String password) {
        this(identifier, password, null, null);
    }

    @AssertTrue(message = "provide a username/email, or both accountCode and employeeNumber")
    public boolean hasValidIdentifier() {
        boolean direct = identifier != null && !identifier.isBlank();
        boolean employee = accountCode != null && !accountCode.isBlank()
                && employeeNumber != null && !employeeNumber.isBlank();
        return direct || employee;
    }
}
