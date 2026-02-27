package com.hiveapp.platform.client.account.dto;

import java.time.Instant;
import java.util.UUID;

import com.hiveapp.platform.client.account.domain.entity.Account.AccountStatus;
import com.hiveapp.platform.client.account.domain.entity.Account.AccountType;

public record AccountDto(
        UUID id,
        UUID userId,
        String accountName,
        AccountStatus status,
        AccountType accountType,
        Instant createdAt,
        Instant updatedAt) {}
