package com.hiveapp.platform.client.collaboration.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.account.domain.repository.CompanyRepository;
import com.hiveapp.platform.client.collaboration.domain.constant.CollaborationStatus;
import com.hiveapp.platform.client.collaboration.domain.entity.Collaboration;
import com.hiveapp.platform.client.collaboration.domain.entity.CollaborationPermission;
import com.hiveapp.platform.client.collaboration.domain.repository.CollaborationRepository;
import com.hiveapp.platform.client.collaboration.domain.repository.CollaborationPermissionRepository;
import com.hiveapp.platform.client.collaboration.service.CollaborationService;
import com.hiveapp.platform.registry.domain.repository.PermissionRepository;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import com.hiveapp.shared.exception.DuplicateResourceException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CollaborationServiceImpl implements CollaborationService {

    private final CollaborationRepository collaborationRepository;
    private final CollaborationPermissionRepository permissionRepository;
    private final AccountRepository accountRepository;
    private final CompanyRepository companyRepository;
    private final PermissionRepository regPermissionRepository;

    @Override
    public Collaboration getCollaboration(UUID id) {
        return collaborationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Collaboration", "id", id));
    }

    @Override
    @Transactional
    public Collaboration initiateCollaboration(UUID clientAccountId, UUID companyId) {
        var client = accountRepository.findById(clientAccountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", clientAccountId));
        var company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company", "id", companyId));

        var provider = company.getAccount();

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
        var collab = getCollaboration(id);
        collab.setStatus(CollaborationStatus.ACTIVE);
        collab.setAcceptedAt(LocalDateTime.now());
        collaborationRepository.save(collab);
    }

    @Override
    @Transactional
    public void revokeCollaboration(UUID id) {
        var collab = getCollaboration(id);
        collab.setStatus(CollaborationStatus.REVOKED);
        collab.setRevokedAt(LocalDateTime.now());
        collaborationRepository.save(collab);
    }

    @Override
    public List<Collaboration> getClientCollaborations(UUID accountId) {
        return collaborationRepository.findAllByClientAccountId(accountId);
    }

    @Override
    public List<Collaboration> getProviderCollaborations(UUID accountId) {
        return collaborationRepository.findAllByProviderAccountId(accountId);
    }

    @Override
    @Transactional
    public void grantPermission(UUID collaborationId, String permissionCode) {
        var collab = getCollaboration(collaborationId);
        var perm = regPermissionRepository.findByCode(permissionCode)
                .orElseThrow(() -> new ResourceNotFoundException("Permission", "code", permissionCode));

        boolean exists = permissionRepository.existsByCollaborationIdAndPermissionId(collaborationId, perm.getId());
        if (exists) {
            throw new DuplicateResourceException("CollaborationPermission", "permissionCode", permissionCode);
        }

        CollaborationPermission cp = new CollaborationPermission();
        cp.setCollaboration(collab);
        cp.setPermission(perm);
        permissionRepository.save(cp);
    }

    @Override
    @Transactional
    public void revokePermission(UUID collaborationId, String permissionCode) {
        var perm = regPermissionRepository.findByCode(permissionCode)
                .orElseThrow(() -> new ResourceNotFoundException("Permission", "code", permissionCode));

        permissionRepository.findByCollaborationIdAndPermissionId(collaborationId, perm.getId())
                .ifPresent(permissionRepository::delete);
    }

    @Override
    public List<CollaborationPermission> getPermissions(UUID collaborationId) {
        return permissionRepository.findAllByCollaborationId(collaborationId);
    }
}
