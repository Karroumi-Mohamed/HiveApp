package com.hiveapp.platform.client.account.service.impl;

import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.account.service.AccountShellService;
import com.hiveapp.shared.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountShellServiceImpl implements AccountShellService {

    private final AccountRepository accountRepository;

    @Override
    public Account getAccount(UUID id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", id));
    }

    @Override
    @Transactional
    public void deactivateAccount(UUID id) {
        var account = getAccount(id);
        account.setActive(false);
        accountRepository.save(account);
    }
}
