package com.hiveapp.platform.client.account.service;

import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.account.domain.entity.AccountBillingProfile;
import com.hiveapp.platform.client.account.domain.repository.AccountBillingProfileRepository;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.account.dto.AccountBillingProfileModels;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountBillingProfileService {
    private final AccountRepository accounts;
    private final AccountBillingProfileRepository profiles;

    @Transactional(readOnly = true)
    public AccountBillingProfileModels.Profile get(UUID accountId) {
        Account account = accounts.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));
        return profiles.findByAccountId(accountId)
                .map(profile -> view(profile, true))
                .orElseGet(() -> new AccountBillingProfileModels.Profile(
                        accountId, account.getName(), null, null, null, null, false));
    }

    @Transactional
    public AccountBillingProfileModels.Profile update(
            UUID accountId,
            AccountBillingProfileModels.UpdateRequest request
    ) {
        Account account = accounts.findByIdForBillingProfileUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId));
        AccountBillingProfile profile = profiles.findByAccountId(accountId)
                .orElseGet(() -> AccountBillingProfile.create(account, request.legalName()));
        profile.update(
                request.legalName(),
                normalize(request.billingEmail()),
                normalize(request.taxId()),
                normalize(request.address()),
                normalizeCountry(request.countryCode()));
        return view(profiles.save(profile), true);
    }

    @Transactional(readOnly = true)
    public AccountBillingProfileModels.Snapshot snapshot(UUID accountId) {
        AccountBillingProfileModels.Profile profile = get(accountId);
        return new AccountBillingProfileModels.Snapshot(
                profile.legalName(), profile.billingEmail(), profile.taxId(),
                profile.address(), profile.countryCode());
    }

    private AccountBillingProfileModels.Profile view(AccountBillingProfile profile, boolean configured) {
        return new AccountBillingProfileModels.Profile(
                profile.getAccount().getId(), profile.getLegalName(), profile.getBillingEmail(),
                profile.getTaxId(), profile.getAddress(), profile.getCountryCode(), configured);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeCountry(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }
}
