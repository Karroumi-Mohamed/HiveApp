package com.hiveapp.shared.security.context;

import com.hiveapp.platform.client.account.domain.entity.Company;
import com.hiveapp.platform.client.account.domain.repository.CompanyRepository;
import com.hiveapp.platform.client.collaboration.domain.constant.CollaborationStatus;
import com.hiveapp.platform.client.collaboration.domain.repository.CollaborationRepository;
import com.hiveapp.platform.client.member.domain.repository.MemberRepository;
import com.hiveapp.shared.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SecurityContextService {

    private final CompanyRepository companyRepository;
    private final MemberRepository memberRepository;
    private final CollaborationRepository collaborationRepository;

    public HiveAppPermissionContext validateAndBuild(UUID userId, String companyIdHeader, String isB2BHeader) {
        if (userId == null) throw new UnauthorizedException("User not authenticated");

        UUID targetCompanyId = null;
        UUID currentAccountId = null;
        boolean isB2B = Boolean.parseBoolean(isB2BHeader);

        if (companyIdHeader != null && !companyIdHeader.isBlank()) {
            try {
                targetCompanyId = UUID.fromString(companyIdHeader);
                
                Company company = companyRepository.findById(targetCompanyId)
                    .orElseThrow(() -> new UnauthorizedException("Requested Company does not exist"));
                
                UUID providerAccountId = company.getAccount().getId();

                if (isB2B) {
                    var member = memberRepository.findFirstByUserId(userId)
                        .orElseThrow(() -> new UnauthorizedException("Access Denied: You are not a member of any account"));
                    UUID clientAccountId = member.getAccount().getId();

                    collaborationRepository.findByClientAccountIdAndProviderAccountIdAndCompanyIdAndStatus(
                        clientAccountId, providerAccountId, targetCompanyId, CollaborationStatus.ACTIVE
                    ).orElseThrow(() -> new UnauthorizedException("Access Denied: No active B2B collaboration found"));
                    
                    currentAccountId = providerAccountId;
                } else {
                    currentAccountId = providerAccountId;
                    memberRepository.findByAccountIdAndUserId(currentAccountId, userId)
                        .orElseThrow(() -> new UnauthorizedException("Access Denied: You are not a member of this account/company"));
                }
            } catch (IllegalArgumentException e) {
                throw new UnauthorizedException("Invalid UUID format in headers");
            }
        } else {
            var member = memberRepository.findFirstByUserId(userId).orElse(null);
            if (member != null) {
                currentAccountId = member.getAccount().getId();
            }
        }

        return new HiveAppPermissionContext(userId, currentAccountId, targetCompanyId, isB2B);
    }
}
