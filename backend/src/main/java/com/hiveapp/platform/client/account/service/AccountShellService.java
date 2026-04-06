package com.hiveapp.platform.client.account.service;

import com.hiveapp.platform.client.account.domain.entity.Account;
import java.util.UUID;
import java.util.List;

public interface AccountShellService {
    Account getAccount(UUID id);
    List<Account> getAllAccounts();
    Account createAccount(UUID ownerId, String name, String slug);
    void deactivateAccount(UUID id);
}
