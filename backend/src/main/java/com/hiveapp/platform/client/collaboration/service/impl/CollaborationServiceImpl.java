package com.hiveapp.platform.client.collaboration.service.impl;

import com.hiveapp.platform.client.account.domain.entity.Account;
import com.hiveapp.platform.client.company.domain.entity.Company;
import com.hiveapp.platform.client.account.domain.repository.AccountRepository;
import com.hiveapp.platform.client.company.domain.repository.CompanyRepository;
import com.hiveapp.platform.client.collaboration.domain.constant.CollaborationStatus;
import com.hiveapp.platform.client.collaboration.domain.constant.SuspensionScheduleAction;
import com.hiveapp.platform.client.collaboration.domain.entity.Collaboration;
import com.hiveapp.platform.client.collaboration.domain.entity.CollaborationPermission;
import com.hiveapp.platform.client.collaboration.domain.repository.CollaborationPermissionRepository;
import com.hiveapp.platform.client.collaboration.domain.repository.CollaborationRepository;
import com.hiveapp.platform.client.collaboration.dto.CollaborationCommandRequest;
import com.hiveapp.platform.client.collaboration.dto.CollaborationDto;
import com.hiveapp.platform.client.collaboration.dto.CollaborationGrantDto;
import com.hiveapp.platform.client.collaboration.dto.CollaborationInitiationResult;
import com.hiveapp.platform.client.collaboration.dto.CompanyShareCodeDto;
import com.hiveapp.platform.client.collaboration.dto.CompanyShareResolutionDto;
import com.hiveapp.platform.client.collaboration.dto.InitiateCollaborationRequest;
import com.hiveapp.platform.client.collaboration.service.CollaborationService;
import com.hiveapp.platform.client.plan.service.PlanEntitlementService;
import com.hiveapp.platform.registry.definition.B2bFeature;
import com.hiveapp.platform.registry.definition.FeatureDefinition;
import com.hiveapp.platform.registry.definition.PermissionGrantValidator;
import com.hiveapp.platform.registry.definition.service.ClientWorkspaceFeatureService;
import com.hiveapp.platform.registry.domain.entity.Permission;
import com.hiveapp.platform.registry.domain.repository.PermissionRepository;
import com.hiveapp.platform.registry.dto.picker.PermissionPickerCatalogDto;
import com.hiveapp.platform.registry.service.PermissionPickerCatalogService;
import com.hiveapp.platform.registry.service.RegistryCatalogVersionService;
import com.hiveapp.shared.exception.DuplicateResourceException;
import com.hiveapp.shared.exception.ForbiddenException;
import com.hiveapp.shared.exception.InvalidPermissionGrantException;
import com.hiveapp.shared.exception.InvalidRequestException;
import com.hiveapp.shared.exception.InvalidStateException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import com.hiveapp.shared.security.DelegationCeilingService;
import com.hiveapp.shared.security.context.HiveAppContextHolder;
import dev.karroumi.permissionizer.PermissionNode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@PermissionNode(key = B2bFeature.KEY, description = "B2B Collaboration Management", guard = PermissionNode.Guard.ON)
public class CollaborationServiceImpl extends ClientWorkspaceFeatureService implements CollaborationService {

    private static final List<CollaborationStatus> LIVE_STATUSES = List.of(
            CollaborationStatus.PENDING, CollaborationStatus.ACTIVE, CollaborationStatus.SUSPENDED);
    private static final List<CollaborationStatus> TERMINAL_STATUSES = List.of(
            CollaborationStatus.CANCELLED, CollaborationStatus.REJECTED, CollaborationStatus.REVOKED);

    private final CollaborationRepository collaborationRepository;
    private final CollaborationInitiationStore collaborationInitiationStore;
    private final CompanyShareCodeUsageRecorder shareCodeUsageRecorder;
    private final CollaborationPermissionRepository collaborationPermissionRepository;
    private final AccountRepository accountRepository;
    private final CompanyRepository companyRepository;
    private final PermissionRepository registryPermissionRepository;
    private final PermissionGrantValidator permissionGrantValidator;
    private final PermissionPickerCatalogService permissionPickerCatalogService;
    private final PlanEntitlementService planEntitlementService;
    private final DelegationCeilingService delegationCeilingService;
    private final RegistryCatalogVersionService catalogVersionService;
    private final Clock clock;
    private final com.hiveapp.platform.communication.BusinessNotifications notifications;

    @Override
    protected FeatureDefinition featureDefinition() {
        return B2bFeature.definition();
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_detail", description = "View collaboration detail and grants")
    public CollaborationDto getCollaboration(UUID id) {
        UUID accountId = currentAccountId();
        return toDto(getParticipantCollaboration(id, accountId), accountId);
    }

    @Override
    @Transactional
    @PermissionNode(key = "request", description = "Request collaboration using a provider share code")
    public CollaborationInitiationResult initiateCollaboration(
            UUID clientAccountId, InitiateCollaborationRequest request) {
        requireCurrentAccount(clientAccountId);
        Account client = accountRepository.findById(clientAccountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", clientAccountId));
        requireActiveAccount(client, "Client Account");
        String shareCodeHash = shareCodeHash(request.shareCode());
        Company company = resolveShareCodeHash(shareCodeHash);
        requireActiveCompany(company);
        Account provider = company.getAccount();
        requireActiveAccount(provider, "Provider Account");
        shareCodeUsageRecorder.recordRequest(company.getId(), shareCodeHash, clock.instant());
        if (provider.getId().equals(clientAccountId)) {
            throw new ForbiddenException("Cannot request B2B collaboration with your own account");
        }
        String normalizedPurpose = normalizePurpose(request.purpose());
        Set<String> requestedPermissions = validateRequestedPermissions(request.requestedPermissionCodes());
        var live = findLiveCollaboration(clientAccountId, provider.getId(), company.getId());
        if (live.isPresent()) {
            return existingOrConflict(live.orElseThrow(), normalizedPurpose, requestedPermissions,
                    clientAccountId);
        }

        boolean hasTerminalHistory = collaborationRepository
                .existsByClientAccountIdAndProviderAccountIdAndCompanyIdAndStatusIn(
                        clientAccountId, provider.getId(), company.getId(), TERMINAL_STATUSES);
        UUID collaborationId;
        try {
            collaborationId = collaborationInitiationStore.create(
                    clientAccountId,
                    provider.getId(),
                    company.getId(),
                    shareCodeHash,
                    normalizedPurpose,
                    requestedPermissions,
                    currentActorId(),
                    clock.instant());
        } catch (DataIntegrityViolationException exception) {
            Collaboration concurrentWinner = findLiveCollaboration(
                    clientAccountId, provider.getId(), company.getId())
                    .orElseThrow(() -> new InvalidStateException(
                            "Collaboration request conflicted concurrently; refresh and retry."));
            return existingOrConflict(
                    concurrentWinner, normalizedPurpose, requestedPermissions, clientAccountId);
        }
        Collaboration created = collaborationRepository.findById(collaborationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Collaboration", "id", collaborationId));
        var outcome = hasTerminalHistory
                ? CollaborationInitiationResult.Outcome.CREATED_AFTER_TERMINAL
                : CollaborationInitiationResult.Outcome.CREATED_INITIAL;
        return new CollaborationInitiationResult(toDto(created, clientAccountId), outcome);
    }

    @Override
    @Transactional
    @PermissionNode(key = "accept", description = "Accept an incoming collaboration request")
    public CollaborationDto acceptCollaboration(
            UUID providerAccountId, UUID id, CollaborationCommandRequest request) {
        requireCurrentAccount(providerAccountId);
        Collaboration collaboration = getProviderCollaboration(id, providerAccountId);
        requireVersion(collaboration, request.expectedVersion());
        requireStatus(collaboration, CollaborationStatus.PENDING,
                "Only pending collaborations can be accepted");
        requireActiveScope(collaboration);
        collaboration.setStatus(CollaborationStatus.ACTIVE);
        collaboration.setAcceptedAt(clock.instant());
        collaboration.setAcceptedByUserId(currentActorId());
        collaboration.setLifecycleReason(null);
        return saveTransition(collaboration, providerAccountId);
    }

    @Override
    @Transactional
    @PermissionNode(key = "reject", description = "Reject an incoming collaboration request")
    public CollaborationDto rejectCollaboration(
            UUID providerAccountId, UUID id, CollaborationCommandRequest request) {
        requireCurrentAccount(providerAccountId);
        Collaboration collaboration = getProviderCollaboration(id, providerAccountId);
        requireVersion(collaboration, request.expectedVersion());
        requireStatus(collaboration, CollaborationStatus.PENDING,
                "Only pending collaborations can be rejected");
        collaboration.setStatus(CollaborationStatus.REJECTED);
        collaboration.setRejectedAt(clock.instant());
        collaboration.setRejectedByUserId(currentActorId());
        collaboration.setLifecycleReason(requireReason(request));
        return saveTransition(collaboration, providerAccountId);
    }

    @Override
    @Transactional
    @PermissionNode(key = "cancel_request", description = "Cancel an outgoing collaboration request")
    public CollaborationDto cancelRequest(
            UUID clientAccountId, UUID id, CollaborationCommandRequest request) {
        requireCurrentAccount(clientAccountId);
        Collaboration collaboration = getParticipantCollaboration(id, clientAccountId);
        requireClient(collaboration, clientAccountId, "Only the requester can cancel this request");
        requireVersion(collaboration, request.expectedVersion());
        requireStatus(collaboration, CollaborationStatus.PENDING,
                "Only pending collaboration requests can be cancelled");
        collaboration.setStatus(CollaborationStatus.CANCELLED);
        collaboration.setCancelledAt(clock.instant());
        collaboration.setCancelledByUserId(currentActorId());
        collaboration.setLifecycleReason(requireReason(request));
        return saveTransition(collaboration, clientAccountId);
    }

    @Override
    @Transactional
    @PermissionNode(key = "suspend", description = "Suspend active delegated collaboration access")
    public CollaborationDto suspendCollaboration(
            UUID providerAccountId, UUID id, CollaborationCommandRequest request) {
        requireCurrentAccount(providerAccountId);
        Collaboration collaboration = getProviderCollaboration(id, providerAccountId);
        requireVersion(collaboration, request.expectedVersion());
        requireStatus(collaboration, CollaborationStatus.ACTIVE,
                "Only active collaborations can be suspended");
        requireActiveScope(collaboration);
        Instant now = clock.instant();
        applySuspensionSchedule(collaboration, request, now);
        collaboration.setStatus(CollaborationStatus.SUSPENDED);
        collaboration.setSuspendedAt(now);
        collaboration.setSuspendedByUserId(currentActorId());
        collaboration.setLifecycleReason(requireReason(request));
        return saveTransition(collaboration, providerAccountId);
    }

    @Override
    @Transactional
    @PermissionNode(key = "resume", description = "Resume suspended delegated collaboration access")
    public CollaborationDto resumeCollaboration(
            UUID providerAccountId, UUID id, CollaborationCommandRequest request) {
        requireCurrentAccount(providerAccountId);
        Collaboration collaboration = getProviderCollaboration(id, providerAccountId);
        requireVersion(collaboration, request.expectedVersion());
        requireStatus(collaboration, CollaborationStatus.SUSPENDED,
                "Only suspended collaborations can be resumed");
        requireActiveScope(collaboration);
        collaboration.setStatus(CollaborationStatus.ACTIVE);
        collaboration.setResumedAt(clock.instant());
        collaboration.setResumedByUserId(currentActorId());
        collaboration.setSuspensionReviewAt(null);
        collaboration.setAutomaticResumeAt(null);
        collaboration.setLifecycleReason(requireReason(request));
        return saveTransition(collaboration, providerAccountId);
    }

    @Override
    @Transactional
    @PermissionNode(key = "revoke", description = "Permanently end an active collaboration")
    public CollaborationDto revokeCollaboration(
            UUID accountId, UUID id, CollaborationCommandRequest request) {
        requireCurrentAccount(accountId);
        Collaboration collaboration = getParticipantCollaboration(id, accountId);
        requireVersion(collaboration, request.expectedVersion());
        if (collaboration.getStatus() != CollaborationStatus.ACTIVE
                && collaboration.getStatus() != CollaborationStatus.SUSPENDED) {
            throw new InvalidStateException("Only active or suspended collaborations can be revoked");
        }
        collaboration.setStatus(CollaborationStatus.REVOKED);
        collaboration.setRevokedAt(clock.instant());
        collaboration.setRevokedByUserId(currentActorId());
        collaboration.setLifecycleReason(requireReason(request));
        return saveTransition(collaboration, accountId);
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "view", description = "View outgoing collaborations")
    public List<CollaborationDto> getClientCollaborations(UUID accountId) {
        requireCurrentAccount(accountId);
        return collaborationRepository.findAllByClientAccountId(accountId).stream()
                .map(collaboration -> toDto(collaboration, accountId))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "view_incoming", description = "View incoming B2B requests")
    public List<CollaborationDto> getProviderCollaborations(UUID accountId) {
        requireCurrentAccount(accountId);
        return collaborationRepository.findAllByProviderAccountId(accountId).stream()
                .map(collaboration -> toDto(collaboration, accountId))
                .toList();
    }

    @Override
    @Transactional
    @PermissionNode(key = "grant_permission", description = "Grant a permission to a B2B client")
    public void grantPermission(
            UUID providerAccountId, UUID collaborationId, String permissionCode, String registryVersion) {
        requireCurrentAccount(providerAccountId);
        Collaboration collaboration = getProviderCollaboration(collaborationId, providerAccountId);
        requireStatus(collaboration, CollaborationStatus.ACTIVE,
                "Permissions can only be granted to an active collaboration");
        requireActiveScope(collaboration);
        catalogVersionService.requireCurrent(registryVersion);

        Permission permission = registryPermissionRepository.findByCode(permissionCode)
                .orElseThrow(() -> new ResourceNotFoundException("Permission", "code", permissionCode));
        permissionGrantValidator.requireB2bDelegatable(permission);
        if (!planEntitlementService.isPermissionEntitled(providerAccountId, permissionCode)) {
            throw new InvalidPermissionGrantException(
                    "Permission " + permissionCode
                            + " is not available in the provider's current plan entitlement.");
        }
        delegationCeilingService.requireActorCanDelegate(
                providerAccountId, collaboration.getCompany().getId(), List.of(permissionCode));
        var existingGrant = collaborationPermissionRepository
                .findByCollaborationIdAndPermissionId(collaborationId, permission.getId());
        if (existingGrant.filter(CollaborationPermission::isActive).isPresent()) {
            throw new DuplicateResourceException(
                    "CollaborationPermission", "permissionCode", permissionCode);
        }
        CollaborationPermission grant = existingGrant.orElseGet(() -> {
                    CollaborationPermission created = new CollaborationPermission();
                    created.setCollaboration(collaboration);
                    created.setPermission(permission);
                    return created;
                });
        grant.setActive(true);
        grant.setGrantedAt(clock.instant());
        grant.setGrantedByUserId(currentActorId());
        grant.setRevokedAt(null);
        grant.setRevokedByUserId(null);
        try {
            collaborationPermissionRepository.saveAndFlush(grant);
        } catch (DataIntegrityViolationException
                 | org.springframework.orm.ObjectOptimisticLockingFailureException exception) {
            throw new DuplicateResourceException(
                    "CollaborationPermission", "permissionCode", permissionCode);
        }
    }

    @Override
    @Transactional
    @PermissionNode(key = "revoke_permission", description = "Revoke a permission from a B2B client")
    public void revokePermission(UUID providerAccountId, UUID collaborationId, String permissionCode) {
        requireCurrentAccount(providerAccountId);
        Collaboration collaboration = getProviderCollaboration(collaborationId, providerAccountId);
        requireStatus(collaboration, CollaborationStatus.ACTIVE,
                "Permissions can only be changed on an active collaboration");
        requireActiveScope(collaboration);
        Permission permission = registryPermissionRepository.findByCode(permissionCode)
                .orElseThrow(() -> new ResourceNotFoundException("Permission", "code", permissionCode));
        collaborationPermissionRepository.findByCollaborationIdAndPermissionId(
                        collaborationId, permission.getId())
                .filter(CollaborationPermission::isActive)
                .ifPresent(grant -> {
                    grant.setActive(false);
                    grant.setRevokedAt(clock.instant());
                    grant.setRevokedByUserId(currentActorId());
                    try {
                        collaborationPermissionRepository.saveAndFlush(grant);
                    } catch (org.springframework.orm.ObjectOptimisticLockingFailureException exception) {
                        throw new InvalidStateException(
                                "Collaboration permission changed concurrently; refresh and retry.");
                    }
                });
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_permissions", description = "View current and historical B2B grants")
    public List<CollaborationGrantDto> getPermissions(UUID collaborationId) {
        UUID accountId = currentAccountId();
        Collaboration collaboration = getParticipantCollaboration(collaborationId, accountId);
        return grantDtos(collaboration);
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "permission_catalog", description = "View B2B-delegatable permission catalog")
    public PermissionPickerCatalogDto getPermissionCatalog(UUID providerAccountId, UUID collaborationId) {
        requireCurrentAccount(providerAccountId);
        Collaboration collaboration = getProviderCollaboration(collaborationId, providerAccountId);
        requireStatus(collaboration, CollaborationStatus.ACTIVE,
                "Permissions can only be granted to an active collaboration");
        requireActiveScope(collaboration);
        Set<String> selections = collaborationPermissionRepository.findAllByCollaborationId(collaborationId).stream()
                .filter(CollaborationPermission::isActive)
                .map(entry -> entry.getPermission().getCode())
                .collect(Collectors.toSet());
        return permissionPickerCatalogService.b2bDelegationCatalog(providerAccountId, selections);
    }

    @Override
    @Transactional
    @PermissionNode(key = "regenerate_share_code", description = "Regenerate a Company B2B share code")
    public CompanyShareCodeDto regenerateCompanyShareCode(UUID providerAccountId, UUID companyId) {
        requireCurrentAccount(providerAccountId);
        Company company = companyRepository.findByIdAndAccountIdForUpdate(companyId, providerAccountId)
                .orElseThrow(() -> new ResourceNotFoundException("Company", "id", companyId));
        requireActiveAccount(company.getAccount(), "Provider Account");
        requireActiveCompany(company);
        String rawCode = "hive_" + UUID.randomUUID().toString().replace("-", "");
        company.setB2bShareCodeHash(hashShareCode(rawCode));
        company.setB2bShareEnabled(true);
        company.setB2bShareGeneratedAt(clock.instant());
        company.setB2bShareResolutionCount(0);
        company.setB2bShareRequestCount(0);
        company.setB2bShareLastResolvedAt(null);
        company.setB2bShareLastRequestedAt(null);
        companyRepository.saveAndFlush(company);
        return shareCodeDto(company, rawCode);
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "read_share_code", description = "View Company share-code state and usage")
    public CompanyShareCodeDto getCompanyShareCode(UUID providerAccountId, UUID companyId) {
        requireCurrentAccount(providerAccountId);
        Company company = companyRepository.findByIdAndAccountId(companyId, providerAccountId)
                .orElseThrow(() -> new ResourceNotFoundException("Company", "id", companyId));
        return shareCodeDto(company, null);
    }

    @Override
    @Transactional
    @PermissionNode(key = "manage_share_code", description = "Enable or disable a Company B2B share code")
    public CompanyShareCodeDto setCompanyShareCodeEnabled(
            UUID providerAccountId, UUID companyId, boolean enabled) {
        requireCurrentAccount(providerAccountId);
        Company company = companyRepository.findByIdAndAccountIdForUpdate(companyId, providerAccountId)
                .orElseThrow(() -> new ResourceNotFoundException("Company", "id", companyId));
        if (enabled) {
            requireActiveAccount(company.getAccount(), "Provider Account");
            requireActiveCompany(company);
            if (company.getB2bShareCodeHash() == null) {
                throw new InvalidStateException("Generate a Company share code before enabling it.");
            }
        }
        company.setB2bShareEnabled(enabled);
        companyRepository.save(company);
        return shareCodeDto(company, null);
    }

    @Override
    @Transactional(readOnly = true)
    @PermissionNode(key = "resolve_share_code", description = "Resolve privacy-minimal Company share identity")
    public CompanyShareResolutionDto resolveCompanyShareCode(String shareCode) {
        String shareCodeHash = shareCodeHash(shareCode);
        Company company = resolveShareCodeHash(shareCodeHash);
        requireActiveAccount(company.getAccount(), "Provider Account");
        requireActiveCompany(company);
        shareCodeUsageRecorder.recordResolution(company.getId(), shareCodeHash, clock.instant());
        return new CompanyShareResolutionDto(
                company.getAccount().getName(), company.getName(), company.getCountry());
    }

    private CollaborationInitiationResult existingOrConflict(
            Collaboration existing,
            String normalizedPurpose,
            Set<String> requestedPermissions,
            UUID viewerAccountId
    ) {
        if (existing.getPurpose().equals(normalizedPurpose)
                && existing.getRequestedPermissionCodes().equals(requestedPermissions)) {
            return new CollaborationInitiationResult(
                    toDto(existing, viewerAccountId),
                    CollaborationInitiationResult.Outcome.EXISTING_IDENTICAL);
        }
        throw new DuplicateResourceException(
                "LiveCollaboration", "client/provider/company", existing.getId());
    }

    private java.util.Optional<Collaboration> findLiveCollaboration(
            UUID clientAccountId, UUID providerAccountId, UUID companyId) {
        return collaborationRepository.findFirstByClientAccountIdAndProviderAccountIdAndCompanyIdAndStatusIn(
                clientAccountId, providerAccountId, companyId, LIVE_STATUSES);
    }

    private CompanyShareCodeDto shareCodeDto(Company company, String rawCode) {
        return new CompanyShareCodeDto(
                company.getId(),
                company.isB2bShareEnabled(),
                rawCode,
                company.getB2bShareGeneratedAt(),
                company.getB2bShareResolutionCount(),
                company.getB2bShareRequestCount(),
                company.getB2bShareLastResolvedAt(),
                company.getB2bShareLastRequestedAt());
    }

    private CollaborationDto saveTransition(Collaboration collaboration, UUID viewerAccountId) {
        try {
            var saved = collaborationRepository.saveAndFlush(collaboration);
            notifications.collaboration(saved);
            return toDto(saved, viewerAccountId);
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException exception) {
            throw new InvalidStateException("Collaboration changed concurrently; refresh and retry.");
        }
    }

    private CollaborationDto toDto(Collaboration collaboration, UUID viewerAccountId) {
        List<String> blockers = accessBlockers(collaboration);
        return new CollaborationDto(
                collaboration.getId(),
                collaboration.getClientAccount().getId(),
                collaboration.getClientAccount().getName(),
                collaboration.getProviderAccount().getId(),
                collaboration.getProviderAccount().getName(),
                collaboration.getCompany().getId(),
                collaboration.getCompany().getName(),
                collaboration.getCompany().getCountry(),
                collaboration.getStatus(),
                collaboration.getVersion(),
                collaboration.getPurpose(),
                Set.copyOf(collaboration.getRequestedPermissionCodes()),
                grantDtos(collaboration),
                allowedNextActions(collaboration, viewerAccountId),
                blockers,
                collaboration.getRequestedAt(),
                collaboration.getAcceptedAt(),
                collaboration.getCancelledAt(),
                collaboration.getRejectedAt(),
                collaboration.getSuspendedAt(),
                collaboration.getSuspensionReviewAt(),
                collaboration.getAutomaticResumeAt(),
                collaboration.getResumedAt(),
                collaboration.getRevokedAt(),
                collaboration.getLifecycleReason());
    }

    private List<CollaborationGrantDto> grantDtos(Collaboration collaboration) {
        boolean scopeActive = collaboration.getStatus() == CollaborationStatus.ACTIVE
                && activeScope(collaboration);
        return collaborationPermissionRepository.findAllByCollaborationId(collaboration.getId()).stream()
                .map(grant -> new CollaborationGrantDto(
                        grant.getPermission().getCode(),
                        grant.getPermission().getDescription(),
                        grant.isActive(),
                        grant.isActive()
                                && scopeActive
                                && permissionGrantValidator.isB2bRuntimeEligible(
                                        grant.getPermission().getCode())
                                && planEntitlementService.isPermissionEntitled(
                                        collaboration.getProviderAccount().getId(),
                                        grant.getPermission().getCode()),
                        grant.getGrantedAt(),
                        grant.getRevokedAt()))
                .toList();
    }

    private List<String> accessBlockers(Collaboration collaboration) {
        List<String> blockers = new ArrayList<>();
        if (!collaboration.getClientAccount().isActive()) blockers.add("CLIENT_ACCOUNT_INACTIVE");
        if (!collaboration.getProviderAccount().isActive()) blockers.add("PROVIDER_ACCOUNT_INACTIVE");
        if (!collaboration.getCompany().isActive()) blockers.add("COMPANY_INACTIVE");
        if (collaboration.getStatus() != CollaborationStatus.ACTIVE) {
            blockers.add("COLLABORATION_" + collaboration.getStatus().name());
        }
        for (CollaborationPermission grant
                : collaborationPermissionRepository.findAllByCollaborationId(collaboration.getId())) {
            if (grant.isActive() && !permissionGrantValidator.isB2bRuntimeEligible(
                    grant.getPermission().getCode())) {
                blockers.add("CODE_B2B_ELIGIBILITY_MISSING:" + grant.getPermission().getCode());
            }
            if (grant.isActive() && !planEntitlementService.isPermissionEntitled(
                    collaboration.getProviderAccount().getId(), grant.getPermission().getCode())) {
                blockers.add("PROVIDER_ENTITLEMENT_MISSING:" + grant.getPermission().getCode());
            }
        }
        return List.copyOf(blockers);
    }

    private List<String> allowedNextActions(Collaboration collaboration, UUID viewerAccountId) {
        boolean provider = collaboration.getProviderAccount().getId().equals(viewerAccountId);
        boolean client = collaboration.getClientAccount().getId().equals(viewerAccountId);
        boolean scopeActive = activeScope(collaboration);
        List<String> actions = new ArrayList<>();
        switch (collaboration.getStatus()) {
            case PENDING -> {
                if (provider && scopeActive) actions.add("ACCEPT");
                if (provider) actions.add("REJECT");
                if (client) actions.add("CANCEL_REQUEST");
            }
            case ACTIVE -> {
                if (provider && scopeActive) actions.addAll(List.of(
                        "SUSPEND", "GRANT_PERMISSION", "REVOKE_PERMISSION"));
                if (provider || client) actions.add("REVOKE");
            }
            case SUSPENDED -> {
                if (provider && scopeActive) actions.add("RESUME");
                if (provider || client) actions.add("REVOKE");
            }
            case CANCELLED, REJECTED, REVOKED -> {
            }
        }
        return List.copyOf(actions);
    }

    private boolean activeScope(Collaboration collaboration) {
        return collaboration.getClientAccount().isActive()
                && collaboration.getProviderAccount().isActive()
                && collaboration.getCompany().isActive();
    }

    private Set<String> validateRequestedPermissions(Collection<String> permissionCodes) {
        Set<String> normalized = permissionCodes == null
                ? new LinkedHashSet<>()
                : permissionCodes.stream()
                        .map(code -> requireText(code, "Requested permission code is required"))
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        for (String code : normalized) {
            Permission permission = registryPermissionRepository.findByCode(code)
                    .orElseThrow(() -> new ResourceNotFoundException("Permission", "code", code));
            permissionGrantValidator.requireB2bDelegatable(permission);
        }
        return normalized;
    }

    private Company resolveShareCodeHash(String shareCodeHash) {
        return companyRepository.findByB2bShareCodeHashAndB2bShareEnabledTrue(shareCodeHash)
                .orElseThrow(() -> new ResourceNotFoundException("CompanyShareCode", "code", "invalid"));
    }

    private String shareCodeHash(String rawCode) {
        return hashShareCode(requireText(rawCode, "Company share code is required"));
    }

    private String hashShareCode(String rawCode) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(rawCode.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void requireActiveScope(Collaboration collaboration) {
        requireActiveAccount(collaboration.getClientAccount(), "Client Account");
        requireActiveAccount(collaboration.getProviderAccount(), "Provider Account");
        requireActiveCompany(collaboration.getCompany());
    }

    private void requireActiveAccount(Account account, String label) {
        if (!account.isActive()) {
            throw new InvalidStateException(label + " is inactive");
        }
    }

    private void requireActiveCompany(Company company) {
        if (!company.isActive()) {
            throw new InvalidStateException(
                    "B2B collaboration operations are unavailable while the company is inactive");
        }
    }

    private void applySuspensionSchedule(
            Collaboration collaboration,
            CollaborationCommandRequest request,
            Instant now
    ) {
        Instant scheduledAt = request.suspensionScheduledAt();
        SuspensionScheduleAction action = request.suspensionScheduleAction();
        if (scheduledAt == null && action == null) {
            collaboration.setSuspensionReviewAt(null);
            collaboration.setAutomaticResumeAt(null);
            return;
        }
        if (scheduledAt == null || action == null) {
            throw new InvalidRequestException(
                    "Suspension schedule time and action must be supplied together.");
        }
        if (!scheduledAt.isAfter(now)) {
            throw new InvalidRequestException("Suspension schedule time must be in the future.");
        }
        if (action == SuspensionScheduleAction.REVIEW) {
            collaboration.setSuspensionReviewAt(scheduledAt);
            collaboration.setAutomaticResumeAt(null);
        } else {
            collaboration.setSuspensionReviewAt(null);
            collaboration.setAutomaticResumeAt(scheduledAt);
        }
    }

    private void requireCurrentAccount(UUID accountId) {
        if (!accountId.equals(currentAccountId())) {
            throw new ForbiddenException("Collaboration does not belong to your account");
        }
    }

    private UUID currentAccountId() {
        var context = HiveAppContextHolder.getContext();
        if (context == null || context.currentAccountId() == null) {
            throw new ForbiddenException("An active workspace context is required");
        }
        return context.currentAccountId();
    }

    private UUID currentActorId() {
        var context = HiveAppContextHolder.getContext();
        if (context == null || context.actorUserId() == null) {
            throw new ForbiddenException("An authenticated actor is required");
        }
        return context.actorUserId();
    }

    private Collaboration getProviderCollaboration(UUID id, UUID providerAccountId) {
        return collaborationRepository.findByIdAndProviderAccountId(id, providerAccountId)
                .orElseThrow(() -> new ResourceNotFoundException("Collaboration", "id", id));
    }

    private Collaboration getParticipantCollaboration(UUID id, UUID accountId) {
        return collaborationRepository.findParticipantById(id, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Collaboration", "id", id));
    }

    private void requireClient(Collaboration collaboration, UUID accountId, String message) {
        if (!collaboration.getClientAccount().getId().equals(accountId)) {
            throw new ForbiddenException(message);
        }
    }

    private void requireStatus(Collaboration collaboration, CollaborationStatus expected, String message) {
        if (collaboration.getStatus() != expected) {
            throw new InvalidStateException(message);
        }
    }

    private void requireVersion(Collaboration collaboration, long expectedVersion) {
        if (collaboration.getVersion() != expectedVersion) {
            throw new InvalidStateException("Collaboration changed; refresh and retry.");
        }
    }

    private String requireReason(CollaborationCommandRequest request) {
        return requireText(request.reason(), "A lifecycle reason is required");
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException(message);
        }
        return value.trim();
    }

    private String normalizePurpose(String purpose) {
        return requireText(purpose, "Collaboration purpose is required").replaceAll("\\s+", " ");
    }
}
