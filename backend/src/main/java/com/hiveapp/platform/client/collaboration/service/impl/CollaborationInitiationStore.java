package com.hiveapp.platform.client.collaboration.service.impl;

import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.account.domain.entity.Company;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.account.domain.repository.CompanyRepository;
import com.hiveapp.platform.client.collaboration.domain.constant.CollaborationStatus;
import com.hiveapp.platform.client.collaboration.domain.entity.Collaboration;
import com.hiveapp.platform.client.collaboration.domain.repository.CollaborationRepository;
import com.hiveapp.shared.exception.InvalidStateException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Isolates the live-tuple insert so a uniqueness loser can be recovered by the
 * caller without leaving its transaction rollback-only.
 */
@Service
@RequiredArgsConstructor
class CollaborationInitiationStore {

    private final CollaborationRepository collaborationRepository;
    private final AccountRepository accountRepository;
    private final CompanyRepository companyRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    UUID create(
            UUID clientAccountId,
            UUID providerAccountId,
            UUID companyId,
            String expectedShareCodeHash,
            String purpose,
            Set<String> requestedPermissions,
            UUID actorId,
            Instant requestedAt
    ) {
        Account client = accountRepository.findById(clientAccountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", clientAccountId));
        Account provider = accountRepository.findById(providerAccountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", providerAccountId));
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company", "id", companyId));

        if (!company.isB2bShareEnabled()
                || !expectedShareCodeHash.equals(company.getB2bShareCodeHash())) {
            throw new ResourceNotFoundException("CompanyShareCode", "code", "invalid");
        }
        if (!client.isActive() || !provider.isActive() || !company.isActive()) {
            throw new InvalidStateException("Collaboration scope changed before the request was created");
        }

        Collaboration collaboration = new Collaboration();
        collaboration.setClientAccount(client);
        collaboration.setProviderAccount(provider);
        collaboration.setCompany(company);
        collaboration.setStatus(CollaborationStatus.PENDING);
        collaboration.setPurpose(purpose);
        collaboration.setRequestedPermissionCodes(requestedPermissions);
        collaboration.setRequestedAt(requestedAt);
        collaboration.setRequestedByUserId(actorId);
        return collaborationRepository.saveAndFlush(collaboration).getId();
    }
}
