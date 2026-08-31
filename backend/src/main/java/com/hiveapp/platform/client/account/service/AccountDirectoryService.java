package com.hiveapp.platform.client.account.service;

import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.account.dto.AccountDirectoryEntryDto;
import com.hiveapp.platform.client.account.dto.AccountIdentityDirectoryEntryDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AccountDirectoryService {
    Page<AccountDirectoryEntryDto> search(String query, Pageable pageable);
    Page<AccountDirectoryEntryDto> search(String query, Boolean active, Pageable pageable);
    List<AccountDirectoryEntryDto> resolve(Collection<UUID> ids);
    List<Account> requireManagedAccounts(Collection<UUID> ids);
    List<AccountIdentityDirectoryEntryDto> resolveIdentities(Collection<UUID> ids);
}
