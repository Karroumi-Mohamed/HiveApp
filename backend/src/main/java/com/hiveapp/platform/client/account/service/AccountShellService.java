package com.hiveapp.platform.client.account.service;

import com.hiveapp.platform.client.account.domain.entity.Account;
import java.util.UUID;

public interface AccountShellService {
    Account getAccount(UUID id);
    void deactivateAccount(UUID id);
}
