package com.hiveapp.platform.client.account.service.impl;

import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.account.dto.AccountDirectoryEntryDto;
import com.hiveapp.platform.client.account.service.AccountDirectoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountDirectoryServiceImpl implements AccountDirectoryService {

    private final AccountRepository accountRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<AccountDirectoryEntryDto> search(String query, Pageable pageable) {
        String normalized = query == null || query.isBlank() ? null : query.trim();
        return accountRepository.searchDirectory(normalized, pageable)
                .map(account -> new AccountDirectoryEntryDto(
                        account.getId(),
                        account.getName(),
                        account.getSlug(),
                        account.getOwner().getEmail(),
                        account.isActive()));
    }
}
