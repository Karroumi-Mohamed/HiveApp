package com.hiveapp.platform.client.account.service.impl;

import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.account.service.AccountShellService;
import com.hiveapp.identity.service.IdentityService;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountShellServiceImpl implements AccountShellService {

    private final AccountRepository accountRepository;
    private final IdentityService identityService;

    @Override
    public Account getAccount(UUID id) {
        return accountRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Account", "id", id));
    }

    @Override
    public List<Account> getAllAccounts() {
        return accountRepository.findAll();
    }

    @Override
    @Transactional
    public Account createAccount(UUID ownerId, String name, String slug) {
        // Verify user exists
        identityService.getUserById(ownerId)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", ownerId));
            
        Account account = new Account();
        account.setOwnerId(ownerId); // Corrected: Set UUID directly
        account.setName(name);
        account.setSlug(slug);
        account.setActive(true);
        return accountRepository.save(account);
    }

    @Override
    @Transactional
    public void deactivateAccount(UUID id) {
        var account = getAccount(id);
        account.setActive(false);
        accountRepository.save(account);
    }
}
