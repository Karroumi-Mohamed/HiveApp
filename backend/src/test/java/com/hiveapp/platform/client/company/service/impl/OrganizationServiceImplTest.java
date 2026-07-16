package com.hiveapp.platform.client.company.service.impl;

import com.hiveapp.platform.client.account.domain.repository.CompanyRepository;
import com.hiveapp.platform.client.company.domain.repository.*;
import com.hiveapp.platform.client.member.domain.repository.MemberRepository;
import com.hiveapp.shared.exception.ForbiddenException;
import com.hiveapp.shared.security.context.HiveAppContextHolder;
import com.hiveapp.shared.security.context.HiveAppPermissionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceImplTest {

    @Mock private CompanyRepository companyRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private OrganizationGroupRepository groupRepository;
    @Mock private GroupMembershipRepository membershipRepository;
    @Mock private GroupStructureTemplateRepository templateRepository;
    @Mock private GroupTemplateNodeRepository templateNodeRepository;

    @InjectMocks private OrganizationServiceImpl organizationService;

    @AfterEach
    void clearContext() {
        HiveAppContextHolder.clearContext();
    }

    @Test
    void b2bContextCannotReadOrganizationFromAnotherProviderCompany() {
        UUID providerAccountId = UUID.randomUUID();
        UUID targetCompanyId = UUID.randomUUID();
        UUID otherCompanyId = UUID.randomUUID();
        HiveAppContextHolder.setContext(new HiveAppPermissionContext(
                UUID.randomUUID(), UUID.randomUUID(), providerAccountId,
                targetCompanyId, UUID.randomUUID(), true));

        assertThatThrownBy(() -> organizationService.listGroups(otherCompanyId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("B2B access is limited to the collaboration company");

        verifyNoInteractions(companyRepository, groupRepository);
    }
}
