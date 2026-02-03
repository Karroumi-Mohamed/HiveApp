package com.hiveapp.collaboration.domain.service;

import com.hiveapp.collaboration.domain.dto.*;
import com.hiveapp.collaboration.domain.entity.Collaboration;
import com.hiveapp.collaboration.domain.entity.CollaborationPermission;
import com.hiveapp.collaboration.domain.mapper.CollaborationMapper;
import com.hiveapp.collaboration.domain.repository.CollaborationPermissionRepository;
import com.hiveapp.collaboration.domain.repository.CollaborationRepository;
import com.hiveapp.collaboration.event.CollaborationPermissionsChangedEvent;
import com.hiveapp.company.domain.entity.Company;
import com.hiveapp.company.domain.repository.CompanyRepository;
import com.hiveapp.shared.exception.BusinessException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CollaborationService {

    private final CollaborationRepository collaborationRepository;
    private final CollaborationPermissionRepository collaborationPermissionRepository;
    private final CollaborationMapper collaborationMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final CompanyRepository companyRepository;

    @Transactional
    public CollaborationResponse createCollaboration(UUID clientAccountId, CreateCollaborationRequest request) {
        if (clientAccountId.equals(request.getProviderAccountId())) {
            throw new BusinessException("Cannot collaborate with yourself");
        }

        // Verify the company belongs to the client account (prevents sub-delegation)
        Company company = companyRepository.findById(request.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Company", "id", request.getCompanyId()));

        if (!company.getAccountId().equals(clientAccountId)) {
            throw new BusinessException(
                    "Company does not belong to the client account. Cannot share a company you do not own.");
        }

        collaborationRepository.findActiveOrPending(
                clientAccountId, request.getProviderAccountId(), request.getCompanyId()
        ).ifPresent(existing -> {
            throw new BusinessException("Active or pending collaboration already exists for this company");
        });

        Collaboration collaboration = Collaboration.builder()
                .clientAccountId(clientAccountId)
                .providerAccountId(request.getProviderAccountId())
                .companyId(request.getCompanyId())
                .build();

        if (request.getPermissionIds() != null) {
            for (UUID permissionId : request.getPermissionIds()) {
                CollaborationPermission cp = CollaborationPermission.builder()
                        .permissionId(permissionId)
                        .build();
                collaboration.addPermission(cp);
            }
        }

        Collaboration saved = collaborationRepository.save(collaboration);
        log.info("Collaboration created: client={}, provider={}, company={}",
                clientAccountId, request.getProviderAccountId(), request.getCompanyId());
        return collaborationMapper.toResponse(saved);
    }

    @Transactional
    public CollaborationResponse acceptCollaboration(UUID id) {
        Collaboration collaboration = findCollaborationOrThrow(id);
        if (!collaboration.isPending()) {
            throw new BusinessException("Collaboration is not in pending status");
        }
        collaboration.accept();
        collaborationRepository.save(collaboration);
        log.info("Collaboration accepted: {}", id);
        return collaborationMapper.toResponse(collaboration);
    }

    @Transactional
    public void revokeCollaboration(UUID id) {
        Collaboration collaboration = findCollaborationOrThrow(id);

        if (collaboration.isRevoked()) {
            throw new BusinessException("Collaboration is already revoked");
        }

        collaboration.revoke();
        collaborationRepository.save(collaboration);

        // Evict cached permissions so provider immediately loses access
        eventPublisher.publishEvent(
                new CollaborationPermissionsChangedEvent(id, collaboration.getProviderAccountId()));

        log.info("Collaboration revoked: {}", id);
    }

    @Transactional
    public void suspendCollaboration(UUID id) {
        Collaboration collaboration = findCollaborationOrThrow(id);

        if (!collaboration.isActive()) {
            throw new BusinessException("Can only suspend an active collaboration");
        }

        collaboration.suspend();
        collaborationRepository.save(collaboration);

        // Evict cached permissions so provider immediately loses access
        eventPublisher.publishEvent(
                new CollaborationPermissionsChangedEvent(id, collaboration.getProviderAccountId()));

        log.info("Collaboration suspended: {}", id);
    }

    @Transactional
    public CollaborationResponse updatePermissions(UUID id, UpdateCollaborationPermissionsRequest request) {
        Collaboration collaboration = collaborationRepository.findByIdWithPermissions(id)
                .orElseThrow(() -> new ResourceNotFoundException("Collaboration", "id", id));

        if (!collaboration.isActive()) {
            throw new BusinessException("Can only modify permissions on an active collaboration");
        }

        collaboration.clearPermissions();
        for (UUID permissionId : request.getPermissionIds()) {
            CollaborationPermission cp = CollaborationPermission.builder()
                    .permissionId(permissionId)
                    .build();
            collaboration.addPermission(cp);
        }

        Collaboration saved = collaborationRepository.save(collaboration);
        eventPublisher.publishEvent(
                new CollaborationPermissionsChangedEvent(id, collaboration.getProviderAccountId())
        );
        log.info("Collaboration permissions updated: {}", id);
        return collaborationMapper.toResponse(saved);
    }

    public CollaborationResponse getCollaborationById(UUID id) {
        Collaboration collaboration = collaborationRepository.findByIdWithPermissions(id)
                .orElseThrow(() -> new ResourceNotFoundException("Collaboration", "id", id));
        return collaborationMapper.toResponse(collaboration);
    }

    public List<CollaborationResponse> getCollaborationsAsClient(UUID clientAccountId) {
        return collaborationMapper.toResponseList(
                collaborationRepository.findByClientAccountId(clientAccountId));
    }

    public List<CollaborationResponse> getCollaborationsAsProvider(UUID providerAccountId) {
        return collaborationMapper.toResponseList(
                collaborationRepository.findByProviderAccountId(providerAccountId));
    }

    public Set<UUID> getCollaborationPermissionIds(UUID collaborationId) {
        return collaborationPermissionRepository.findPermissionIdsByCollaborationId(collaborationId);
    }

    public List<Collaboration> getActiveCollaborationsForProvider(UUID providerAccountId) {
        return collaborationRepository.findActiveByProviderWithPermissions(providerAccountId);
    }

    public Collaboration findCollaborationOrThrow(UUID id) {
        return collaborationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Collaboration", "id", id));
    }
}
