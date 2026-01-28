package com.hiveapp.account.domain.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class AccountResponse {
    private UUID id;
    private UUID ownerId;
    private UUID planId;
    private String name;
    private String slug;
    private boolean active;
    private Instant createdAt;
}
