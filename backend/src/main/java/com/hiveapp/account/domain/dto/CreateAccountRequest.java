package com.hiveapp.account.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class CreateAccountRequest {

    @NotBlank(message = "Account name is required")
    @Size(max = 150)
    private String name;

    private UUID planId;
}
