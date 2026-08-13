package com.hiveapp.platform.client.account.service;

import com.hiveapp.platform.client.account.dto.AccountDirectoryEntryDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AccountDirectoryService {
    Page<AccountDirectoryEntryDto> search(String query, Pageable pageable);
}
