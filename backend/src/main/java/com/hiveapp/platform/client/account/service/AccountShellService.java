package com.hiveapp.platform.client.account.service;

import com.hiveapp.platform.client.account.dto.AccountDto;
import java.util.UUID;

public interface AccountShellService {
    AccountDto getAccount(UUID id);
    void deactivateAccount(UUID id);
}
