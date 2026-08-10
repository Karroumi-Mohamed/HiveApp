package com.hiveapp.platform.client.collaboration.dto;

import jakarta.validation.constraints.NotBlank;

public record ShareCodeRequest(@NotBlank String shareCode) {}
