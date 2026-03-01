package com.hiveapp.platform.client.account.service.impl;

import java.util.UUID;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.hiveapp.identity.event.UserRegisteredEvent;
import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.account.dto.AccountDto;
import com.hiveapp.platform.client.account.mapper.AccountMapper;
import com.hiveapp.platform.client.account.service.AccountService;
import com.hiveapp.shared.exception.ResourceNotFoundException;

import com.hiveapp.platform.client.account.event.AccountCreatedEvent;
import com.hiveapp.shared.event.EventPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {
    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;
    private final EventPublisher eventPublisher;

    @ApplicationModuleListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onUserRegistered(UserRegisteredEvent event) {
        if (accountRepository.existsByUserId(event.userId())) {
            log.warn("Account already exists for user: {}", event.userId());
            return;
        }

        Account account = Account.builder()
                .userId(event.userId())
                .accountName(event.email().split("@")[0]) 
                .build();
        
        accountRepository.save(account);
        log.info("Created account for user: {}", event.userId());
        eventPublisher.publish(new AccountCreatedEvent(account.getId(), event.userId()));
    }

    @Override
    @Transactional(readOnly = true)
    public AccountDto getAccountByUserId(UUID userId) {
        Account account = accountRepository.findByUserId(userId)
                .orElseThrow(()-> new ResourceNotFoundException("Account", "userId", userId.toString()));
        return accountMapper.toDto(account);
    }

    
}
