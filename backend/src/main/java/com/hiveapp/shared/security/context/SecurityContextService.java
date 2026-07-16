package com.hiveapp.shared.security.context;

import com.hiveapp.platform.client.account.domain.entity.Company;
import com.hiveapp.platform.client.account.domain.repository.CompanyRepository;
import com.hiveapp.platform.client.collaboration.domain.constant.CollaborationStatus;
import com.hiveapp.platform.client.collaboration.domain.repository.CollaborationRepository;
import com.hiveapp.platform.client.member.domain.repository.MemberRepository;
import com.hiveapp.shared.exception.ForbiddenException;
import com.hiveapp.shared.exception.InvalidRequestException;
import com.hiveapp.shared.exception.ResourceNotFoundException;
import com.hiveapp.shared.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SecurityContextService {

    private final CompanyRepository companyRepository;
    private final MemberRepository memberRepository;
    private final CollaborationRepository collaborationRepository;

    @Transactional(readOnly = true)
    public HiveAppPermissionContext validateAndBuild(UUID userId, String companyIdHeader, String isB2BHeader) {
        if (userId == null) throw new UnauthorizedException("User not authenticated");

        UUID targetCompanyId = null;
        UUID currentAccountId = null;
        UUID clientAccountId = null;
        UUID collaborationId = null;
        boolean isB2B = Boolean.parseBoolean(isB2BHeader);

        if (companyIdHeader != null && !companyIdHeader.isBlank()) {
            try {
                UUID requestedCompanyId = UUID.fromString(companyIdHeader);
                targetCompanyId = requestedCompanyId;
                
                Company company = companyRepository.findById(requestedCompanyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Company", "id", requestedCompanyId));
                requireActiveAccount(company.getAccount());
                UUID providerAccountId = company.getAccount().getId();

                if (isB2B) {
                    var member = memberRepository.findByUserIdAndIsActiveTrue(userId)
                        .orElseThrow(() -> new UnauthorizedException("Access Denied: You are not a member of any account"));
                    requireActiveAccount(member.getAccount());
                    clientAccountId = member.getAccount().getId();

                    var collaboration = collaborationRepository.findByClientAccountIdAndProviderAccountIdAndCompanyIdAndStatus(
                        clientAccountId, providerAccountId, targetCompanyId, CollaborationStatus.ACTIVE
                    ).orElseThrow(() -> new ForbiddenException("Access Denied: No active B2B collaboration found"));
                    
                    collaborationId = collaboration.getId();
                    currentAccountId = providerAccountId;
                    requireActiveCompany(company);
                } else {
                    currentAccountId = providerAccountId;
                    clientAccountId = currentAccountId;
                    var member = memberRepository.findByAccountIdAndUserId(currentAccountId, userId)
                        .orElseThrow(() -> new ForbiddenException("Access Denied: You are not a member of this account/company"));
                    requireActiveMember(member);
                    requireActiveCompany(company);
                }
            } catch (IllegalArgumentException e) {
                throw new InvalidRequestException("Invalid UUID format in X-Company-ID header");
            }
        } else {
            var member = memberRepository.findByUserIdAndIsActiveTrue(userId).orElse(null);
            if (member != null) {
                requireActiveAccount(member.getAccount());
                currentAccountId = member.getAccount().getId();
                clientAccountId = currentAccountId;
            } else if (memberRepository.existsByUserId(userId)) {
                throw new UnauthorizedException("Access Denied: Workspace membership is inactive");
            }
        }

        return new HiveAppPermissionContext(userId, clientAccountId, currentAccountId, targetCompanyId, collaborationId, isB2B);
    }

    private void requireActiveMember(com.hiveapp.platform.client.member.domain.entity.Member member) {
        if (!member.isActive()) {
            throw new UnauthorizedException("Access Denied: Workspace membership is inactive");
        }
        requireActiveAccount(member.getAccount());
    }

    private void requireActiveAccount(com.hiveapp.platform.client.account.domain.entity.Account account) {
        if (!account.isActive()) {
            throw new ForbiddenException("Access Denied: Workspace account is suspended");
        }
    }

    private void requireActiveCompany(Company company) {
        if (!company.isActive()) {
            throw new ForbiddenException("Access Denied: Company is inactive");
        }
    }
}
