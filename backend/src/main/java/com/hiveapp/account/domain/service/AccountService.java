package com.hiveapp.account.domain.service;

import com.hiveapp.account.domain.dto.*;
import com.hiveapp.account.domain.entity.Account;
import com.hiveapp.account.domain.mapper.AccountMapper;
import com.hiveapp.account.domain.repository.AccountRepository;
import com.hiveapp.account.event.AccountCreatedEvent;
import com.hiveapp.plan.event.PlanChangedEvent;
import com.hiveapp.shared.exception.DuplicateResourceException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import com.hiveapp.shared.util.SlugUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountService {

    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public AccountResponse createAccount(UUID ownerId, CreateAccountRequest request) {
        if (accountRepository.existsByOwnerId(ownerId)) {
            throw new DuplicateResourceException("Account", "ownerId", ownerId);
        }

        String slug = generateUniqueSlug(request.getName());

        Account account = Account.builder()
                .ownerId(ownerId)
                .planId(request.getPlanId())
                .name(request.getName())
                .slug(slug)
                .build();

        Account saved = accountRepository.save(account);
        log.info("Account created: {} (slug: {})", saved.getId(), saved.getSlug());

        eventPublisher.publishEvent(new AccountCreatedEvent(saved.getId(), saved.getOwnerId(), saved.getPlanId()));

        return accountMapper.toResponse(saved);
    }

    @Transactional
    public AccountResponse updateAccount(UUID id, UpdateAccountRequest request) {
        Account account = findAccountOrThrow(id);

        if (request.getName() != null) {
            account.setName(request.getName());
        }

        Account saved = accountRepository.save(account);
        log.info("Account updated: {}", saved.getId());
        return accountMapper.toResponse(saved);
    }

    @Transactional
    public AccountResponse changePlan(UUID id, UUID newPlanId) {
        Account account = findAccountOrThrow(id);
        UUID oldPlanId = account.getPlanId();
        account.changePlan(newPlanId);

        Account saved = accountRepository.save(account);
        log.info("Account {} plan changed from {} to {}", saved.getId(), oldPlanId, newPlanId);

        eventPublisher.publishEvent(new PlanChangedEvent(newPlanId, saved.getId(), oldPlanId, newPlanId));

        return accountMapper.toResponse(saved);
    }

    @Transactional
    public AccountResponse suspendAccount(UUID id) {
        Account account = findAccountOrThrow(id);
        account.suspend();

        Account saved = accountRepository.save(account);
        log.info("Account suspended: {}", saved.getId());
        return accountMapper.toResponse(saved);
    }

    @Transactional
    public AccountResponse activateAccount(UUID id) {
        Account account = findAccountOrThrow(id);
        account.activate();

        Account saved = accountRepository.save(account);
        log.info("Account activated: {}", saved.getId());
        return accountMapper.toResponse(saved);
    }

    public AccountResponse getAccountById(UUID id) {
        Account account = findAccountOrThrow(id);
        return accountMapper.toResponse(account);
    }

    public AccountResponse getAccountBySlug(String slug) {
        Account account = accountRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "slug", slug));
        return accountMapper.toResponse(account);
    }

    public AccountResponse getAccountByOwnerId(UUID ownerId) {
        Account account = accountRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "ownerId", ownerId));
        return accountMapper.toResponse(account);
    }

    private Account findAccountOrThrow(UUID id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", id));
    }

    private String generateUniqueSlug(String name) {
        String baseSlug = SlugUtil.slugify(name);
        String slug = baseSlug;
        int counter = 1;

        while (accountRepository.existsBySlug(slug)) {
            slug = baseSlug + "-" + counter;
            counter++;
        }

        return slug;
    }
}
