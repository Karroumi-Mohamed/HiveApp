package com.hiveapp.shared.security.context;

import com.hiveapp.platform.client.account.domain.entity.Company;
import com.hiveapp.platform.client.account.domain.repository.CompanyRepository;
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

    public HiveAppPermissionContext validateAndBuild(UUID userId, String companyIdHeader, String isB2BHeader) {
        if (userId == null) throw new UnauthorizedException("User not authenticated");

        UUID targetCompanyId = null;
        UUID currentAccountId = null;
        boolean isB2B = Boolean.parseBoolean(isB2BHeader);

        if (companyIdHeader != null && !companyIdHeader.isBlank()) {
            try {
                targetCompanyId = UUID.fromString(companyIdHeader);
                
                // 1. Verify Company exists and get its owning account
                Company company = companyRepository.findById(targetCompanyId)
                    .orElseThrow(() -> new UnauthorizedException("Requested Company does not exist"));
                
                currentAccountId = company.getAccount().getId();

                // 2. VITAL SECURITY CHECK: Is the user a member of this account?
                // In a production system, if isB2B is true, we would check the Collaboration table here.
                memberRepository.findByAccountIdAndUserId(currentAccountId, userId)
                    .orElseThrow(() -> new UnauthorizedException("Access Denied: You are not a member of this account/company"));

            } catch (IllegalArgumentException e) {
                throw new UnauthorizedException("Invalid UUID format in headers");
            }
        }

        return new HiveAppPermissionContext(userId, currentAccountId, targetCompanyId, isB2B);
    }
}
