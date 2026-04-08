package com.hiveapp.platform.client.collaboration.service.impl;

import com.hiveapp.platform.client.collaboration.domain.entity.Collaboration;
import com.hiveapp.platform.client.collaboration.domain.repository.CollaborationRepository;
import com.hiveapp.platform.client.collaboration.service.CollaborationService;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.account.domain.repository.CompanyRepository;
import com.hiveapp.platform.client.collaboration.domain.constant.CollaborationStatus;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CollaborationServiceImpl implements CollaborationService {

    private final CollaborationRepository collaborationRepository;
    private final AccountRepository accountRepository;
    private final CompanyRepository companyRepository;

    @Override
    @Transactional
    public Collaboration initiateCollaboration(UUID clientAccountId, UUID providerAccountId, UUID companyId) {
        var client = accountRepository.findById(clientAccountId)
            .orElseThrow(() -> new ResourceNotFoundException("Account", "id", clientAccountId));
        var provider = accountRepository.findById(providerAccountId)
            .orElseThrow(() -> new ResourceNotFoundException("Account", "id", providerAccountId));
        var company = companyRepository.findById(companyId)
            .orElseThrow(() -> new ResourceNotFoundException("Company", "id", companyId));

        Collaboration collab = new Collaboration();
        collab.setClientAccount(client);
        collab.setProviderAccount(provider);
        collab.setCompany(company);
        collab.setStatus(CollaborationStatus.PENDING);
        collab.setRequestedAt(LocalDateTime.now());
        return collaborationRepository.save(collab);
    }

    @Override
    @Transactional
    public void acceptCollaboration(UUID id) {
        var collab = collaborationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Collaboration", "id", id));
        collab.setStatus(CollaborationStatus.ACTIVE);
        collab.setAcceptedAt(LocalDateTime.now());
        collaborationRepository.save(collab);
    }

    @Override
    @Transactional
    public void revokeCollaboration(UUID id) {
        var collab = collaborationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Collaboration", "id", id));
        collab.setStatus(CollaborationStatus.REVOKED);
        collab.setRevokedAt(LocalDateTime.now());
        collaborationRepository.save(collab);
    }

    @Override
    public List<Collaboration> getClientCollaborations(UUID accountId) {
        return collaborationRepository.findAllByClientAccountId(accountId);
    }
}
