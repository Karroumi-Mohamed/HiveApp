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
import com.hiveapp.platform.client.plan.service.PlanEntitlementService;
import com.hiveapp.platform.registry.definition.B2bFeature;
import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.definition.PermissionGrantValidator;
import com.hiveapp.platform.registry.definition.service.ClientWorkspaceFeatureService;
import com.hiveapp.platform.registry.domain.repository.PermissionRepository;
import com.hiveapp.platform.registry.dto.PermissionPickerModuleDto;
import com.hiveapp.platform.registry.service.PermissionPickerCatalogService;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import com.hiveapp.shared.exception.DuplicateResourceException;
import com.hiveapp.shared.exception.ForbiddenException;
import com.hiveapp.shared.exception.InvalidPermissionGrantException;
import com.hiveapp.shared.exception.InvalidStateException;
import com.hiveapp.shared.security.context.HiveAppContextHolder;

import dev.karroumi.permissionizer.PermissionNode;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@PermissionNode(key = B2bFeature.KEY, description = "B2B Collaboration Management", guard = PermissionNode.Guard.ON)
public class CollaborationServiceImpl extends ClientWorkspaceFeatureService implements CollaborationService {

    private final CollaborationRepository collaborationRepository;
    private final CollaborationPermissionRepository permissionRepository;
    private final AccountRepository accountRepository;
    private final CompanyRepository companyRepository;
    private final PermissionRepository regPermissionRepository;
    private final PermissionGrantValidator permissionGrantValidator;
    private final PermissionPickerCatalogService permissionPickerCatalogService;
    private final PlanEntitlementService planEntitlementService;

    @Override
    protected FeatureDefinition featureDefinition() {
        return B2bFeature.definition();
    }

    @Override
    public Collaboration getCollaboration(UUID id) {
        return collaborationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Collaboration", "id", id));
    }

    @Override
    @Transactional
    @PermissionNode(key = "request", description = "Request collaboration with provider")
    public Collaboration initiateCollaboration(UUID clientAccountId, UUID companyId) {
        requireCurrentAccount(clientAccountId);
        var client = accountRepository.findById(clientAccountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", clientAccountId));
        var company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company", "id", companyId));

        var provider = company.getAccount();
        if (provider.getId().equals(clientAccountId)) {
            throw new ForbiddenException("Cannot request B2B collaboration with your own account");
        }

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
    @PermissionNode(key = "accept", description = "Accept incoming collaboration")
    public void acceptCollaboration(UUID providerAccountId, UUID id) {
        var collab = getCollaboration(id);
        requireProvider(collab, providerAccountId, "Not authorized to accept this collaboration request");
        requireStatus(collab, CollaborationStatus.PENDING, "Only pending collaborations can be accepted");
        collab.setStatus(CollaborationStatus.ACTIVE);
        collab.setAcceptedAt(LocalDateTime.now());
        collaborationRepository.save(collab);
    }

    @Override
    @Transactional
    @PermissionNode(key = "revoke", description = "Revoke collaboration")
    public void revokeCollaboration(UUID accountId, UUID id) {
        var collab = getCollaboration(id);
        requireParticipant(collab, accountId, "Not authorized to revoke this collaboration");
        if (collab.getStatus() == CollaborationStatus.REVOKED) {
            throw new InvalidStateException("Collaboration is already revoked");
        }
        collab.setStatus(CollaborationStatus.REVOKED);
        collab.setRevokedAt(LocalDateTime.now());
        collaborationRepository.save(collab);
    }

    @Override
    @PermissionNode(key = "view", description = "View my collaborations")
    public List<Collaboration> getClientCollaborations(UUID accountId) {
        requireCurrentAccount(accountId);
        return collaborationRepository.findAllByClientAccountId(accountId);
    }

    @Override
    @PermissionNode(key = "view_incoming", description = "View incoming B2B requests")
    public List<Collaboration> getProviderCollaborations(UUID accountId) {
        requireCurrentAccount(accountId);
        return collaborationRepository.findAllByProviderAccountId(accountId);
    }

    @Override
    @Transactional
    @PermissionNode(key = "grant_permission", description = "Grant permissions to this B2B client")
    public void grantPermission(UUID providerAccountId, UUID collaborationId, String permissionCode) {
        var collab = getCollaboration(collaborationId);
        requireProvider(collab, providerAccountId, "Only the provider can grant B2B permissions");
        requireStatus(collab, CollaborationStatus.ACTIVE, "Permissions can only be granted to an active collaboration");

        var perm = regPermissionRepository.findByCode(permissionCode)
                .orElseThrow(() -> new ResourceNotFoundException("Permission", "code", permissionCode));
        permissionGrantValidator.requireB2bDelegatable(perm);
        if (!planEntitlementService.isPermissionEntitled(providerAccountId, permissionCode)) {
            throw new InvalidPermissionGrantException(
                    "Permission " + permissionCode + " is not available in the provider's current plan entitlement.");
        }

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
    @PermissionNode(key = "revoke_permission", description = "Revoke permissions from this B2B client")
    public void revokePermission(UUID providerAccountId, UUID collaborationId, String permissionCode) {
        var collab = getCollaboration(collaborationId);
        requireProvider(collab, providerAccountId, "Only the provider can revoke B2B permissions");

        var perm = regPermissionRepository.findByCode(permissionCode)
                .orElseThrow(() -> new ResourceNotFoundException("Permission", "code", permissionCode));

        permissionRepository.findByCollaborationIdAndPermissionId(collaborationId, perm.getId())
                .ifPresent(permissionRepository::delete);
    }

    @Override
    public List<CollaborationPermission> getPermissions(UUID collaborationId) {
        var collab = getCollaboration(collaborationId);
        UUID accountId = HiveAppContextHolder.getContext().currentAccountId();
        requireParticipant(collab, accountId, "Not authorized to view this collaboration's permissions");
        return permissionRepository.findAllByCollaborationId(collaborationId);
    }

    @Override
    @PermissionNode(key = "permission_catalog", description = "View B2B-delegatable permission catalog")
    public List<PermissionPickerModuleDto> getPermissionCatalog(UUID providerAccountId, UUID collaborationId) {
        var collab = getCollaboration(collaborationId);
        requireProvider(collab, providerAccountId, "Only the provider can view grantable B2B permissions");
        requireStatus(collab, CollaborationStatus.ACTIVE, "Permissions can only be granted to an active collaboration");
        return permissionPickerCatalogService.b2bDelegationCatalog(providerAccountId);
    }

    private void requireProvider(Collaboration collaboration, UUID accountId, String message) {
        if (!collaboration.getProviderAccount().getId().equals(accountId)) {
            throw new ForbiddenException(message);
        }
    }

    private void requireParticipant(Collaboration collaboration, UUID accountId, String message) {
        if (!collaboration.getProviderAccount().getId().equals(accountId)
                && !collaboration.getClientAccount().getId().equals(accountId)) {
            throw new ForbiddenException(message);
        }
    }

    private void requireCurrentAccount(UUID accountId) {
        UUID currentAccountId = HiveAppContextHolder.getContext().currentAccountId();
        if (!accountId.equals(currentAccountId)) {
            throw new ForbiddenException("Collaboration does not belong to your account");
        }
    }

    private void requireStatus(Collaboration collaboration, CollaborationStatus status, String message) {
        if (collaboration.getStatus() != status) {
            throw new InvalidStateException(message);
        }
    }
}
