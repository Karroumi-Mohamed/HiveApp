package com.hiveapp.account.domain.dto;

import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UpdateAccountRequest {

    @Size(max = 150)
    private String name;
}
