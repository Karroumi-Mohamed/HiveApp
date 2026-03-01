package com.hiveapp.platform.client.account.service;

import java.util.UUID;

import com.hiveapp.platform.client.account.dto.AccountDto;

public interface AccountService {
    AccountDto getAccountByUserId(UUID userId);
}
